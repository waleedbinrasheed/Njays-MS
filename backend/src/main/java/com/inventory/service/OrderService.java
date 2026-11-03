package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.order.OrderDtos.CalendarDateType;
import com.inventory.dto.order.OrderDtos.CalendarItem;
import com.inventory.dto.order.OrderDtos.CancelRequest;
import com.inventory.dto.order.OrderDtos.OrderLineRequest;
import com.inventory.dto.order.OrderDtos.OrderRequest;
import com.inventory.dto.order.OrderDtos.OrderResponse;
import com.inventory.dto.order.OrderDtos.OrderSummary;
import com.inventory.dto.order.OrderDtos.PaymentRequest;
import com.inventory.dto.order.OrderDtos.StatusRequest;
import com.inventory.entity.AppUser;
import com.inventory.entity.Customer;
import com.inventory.entity.CustomerOrder;
import com.inventory.entity.FabricSource;
import com.inventory.entity.GarmentType;
import com.inventory.entity.MeasurementRecord;
import com.inventory.entity.OrderLine;
import com.inventory.entity.OrderPayment;
import com.inventory.entity.OrderStatus;
import com.inventory.entity.OrderStatusHistory;
import com.inventory.entity.Product;
import com.inventory.entity.ProductType;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.OrderMapper;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.CustomerOrderRepository;
import com.inventory.repository.CustomerRepository;
import com.inventory.repository.GarmentTypeRepository;
import com.inventory.repository.MeasurementRecordRepository;
import com.inventory.repository.OrderPaymentRepository;
import com.inventory.repository.OrderStatusHistoryRepository;
import com.inventory.repository.ProductRepository;
import com.inventory.security.CurrentUser;
import com.inventory.service.InventoryService.Movement;
import com.inventory.service.DocumentNumberService.DocumentType;
import com.inventory.dto.master.MasterDataDtos.Ref;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.metres;
import static com.inventory.service.MasterDataSupport.money;
import static com.inventory.service.MasterDataSupport.plain;
import static com.inventory.service.MasterDataSupport.roundMoney;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Garment orders. Business rules, all enforced here (the database checks the arithmetic only):
 * <ul>
 *   <li>Totals are computed from the lines on the server; a line's fabric price is copied from the fabric
 *       when the order is saved and never changes afterwards.</li>
 *   <li>STOCK lines issue fabric through {@link InventoryService} (stock never below zero). CUSTOMER lines
 *       touch no stock.</li>
 *   <li>Measurements are copied from the chosen record onto the line when the order is saved.</li>
 *   <li>Only RECEIVED, CUTTING and STITCHING orders can be edited. Stages move one step at a time.</li>
 *   <li>Payments and status history are append-only. A payment cannot exceed the balance due.</li>
 * </ul>
 */
@Service
public class OrderService {

    /** Calendar windows are limited so one request stays small. */
    private static final int MAX_CALENDAR_DAYS = 93;
    private static final List<OrderStatus> CLOSED = List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final CustomerOrderRepository orders;
    private final CustomerRepository customers;
    private final GarmentTypeRepository types;
    private final ProductRepository products;
    private final MeasurementRecordRepository records;
    private final OrderPaymentRepository payments;
    private final OrderStatusHistoryRepository history;
    private final AppUserRepository users;
    private final InventoryService inventory;
    private final DocumentNumberService numbers;
    private final UserDirectory directory;
    private final Clock clock;
    private final ZoneId zone;

    public OrderService(CustomerOrderRepository orders, CustomerRepository customers, GarmentTypeRepository types,
                        ProductRepository products, MeasurementRecordRepository records,
                        OrderPaymentRepository payments, OrderStatusHistoryRepository history,
                        AppUserRepository users, InventoryService inventory, DocumentNumberService numbers,
                        UserDirectory directory, Clock clock, ZoneId businessZone) {
        this.orders = orders;
        this.customers = customers;
        this.types = types;
        this.products = products;
        this.records = records;
        this.payments = payments;
        this.history = history;
        this.users = users;
        this.inventory = inventory;
        this.numbers = numbers;
        this.directory = directory;
        this.clock = clock;
        this.zone = businessZone;
    }

    public record OrderFilter(String search, OrderStatus status, Long customerId, LocalDate dueFrom, LocalDate dueTo) {
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(OrderFilter f, Pageable pageable) {
        if (f.dueFrom() != null && f.dueTo() != null && f.dueTo().isBefore(f.dueFrom())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Page<CustomerOrder> page = orders.search(searchTerm(f.search()), f.status(), f.customerId(),
                f.dueFrom(), f.dueTo(), pageable);
        Map<Long, BigDecimal> paid = paidPerOrder(page.getContent());
        return PageResponse.of(page, o -> OrderMapper.toSummary(o, paid.getOrDefault(o.getId(), BigDecimal.ZERO)));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        Customer customer = activeCustomer(request.customerId());
        CustomerOrder order = new CustomerOrder(numbers.next(DocumentType.ORDER), customer, OrderStatus.RECEIVED);
        applyHeader(order, request, today());
        buildLines(order, request.lines());
        orders.saveAndFlush(order);

        history.save(new OrderStatusHistory(order, null, OrderStatus.RECEIVED, now(), currentActor(), "Order received"));
        Map<Long, BigDecimal> used = fabricUse(order.getLines());
        if (!used.isEmpty()) {
            inventory.applyAll(issues(used, order, "Order " + order.getOrderNumber()));
        }
        return detail(order);
    }

    /**
     * Changes a RECEIVED, CUTTING or STITCHING order. Fabric is adjusted by the net difference per fabric,
     * so a fabric kept on the order is never returned and taken again, and the stock check runs once on the
     * final figure.
     */
    @Transactional
    public OrderResponse update(Long id, OrderRequest request) {
        CustomerOrder order = load(id);
        checkVersion(order, request.version());
        if (!order.getStatus().isEditable()) {
            throw new BusinessException(HttpStatus.CONFLICT, "This order is at " + order.getStatus().name()
                    + " and can no longer be changed");
        }
        Map<Long, BigDecimal> before = fabricUse(order.getLines());
        if (!order.getCustomer().getId().equals(request.customerId())) {
            order.setCustomer(activeCustomer(request.customerId()));
        }
        applyHeader(order, request, order.getOrderDate());
        order.clearLines();
        orders.flush();
        buildLines(order, request.lines());
        orders.flush();

        Map<Long, BigDecimal> after = fabricUse(order.getLines());
        Map<Long, BigDecimal> net = new LinkedHashMap<>();
        after.forEach((productId, metres) -> net.merge(productId, metres, BigDecimal::add));
        before.forEach((productId, metres) -> net.merge(productId, metres.negate(), BigDecimal::add));
        List<Movement> moves = new ArrayList<>();
        String note = "Order " + order.getOrderNumber() + " changed";
        net.forEach((productId, extra) -> {
            if (extra.signum() == 0) return;
            BigDecimal change = extra.negate();
            TransactionType type = change.signum() < 0 ? TransactionType.ORDER_ISSUE : TransactionType.ORDER_CANCEL;
            moves.add(new Movement(productId, type, change, ReferenceType.ORDER, order.getId(), note));
        });
        if (!moves.isEmpty()) {
            inventory.applyAll(moves);
        }
        return detail(order);
    }

    /** Moves the order one step forward or back. CANCELLED is only reached through {@link #cancel}. */
    @Transactional
    public OrderResponse changeStatus(Long id, StatusRequest request) {
        CustomerOrder order = load(id);
        checkVersion(order, request.version());
        OrderStatus from = order.getStatus();
        OrderStatus to = request.status();
        if (from == OrderStatus.CANCELLED) {
            throw new BusinessException("A cancelled order cannot change status");
        }
        if (to == OrderStatus.CANCELLED) {
            throw new BusinessException("Use Cancel to cancel an order");
        }
        if (from == to) {
            throw new BusinessException("The order is already at " + to.name());
        }
        if (Math.abs(OrderStatus.PIPELINE.indexOf(to) - OrderStatus.PIPELINE.indexOf(from)) != 1) {
            throw new BusinessException("Move the order one stage at a time, forward or back");
        }
        order.setStatus(to);
        history.save(new OrderStatusHistory(order, from, to, now(), currentActor(), trimToNull(request.note())));
        orders.saveAndFlush(order);
        return detail(order);
    }

    /**
     * Cancels an order and returns its STOCK fabric. Refused once delivered, and while any payment is
     * recorded (refunds are handled outside the system).
     */
    @Transactional
    public OrderResponse cancel(Long id, CancelRequest request) {
        CustomerOrder order = lockOrder(id);
        checkVersion(order, request.version());
        OrderStatus from = order.getStatus();
        if (from == OrderStatus.DELIVERED || from == OrderStatus.CANCELLED) {
            throw new BusinessException("A " + from.name().toLowerCase() + " order cannot be cancelled");
        }
        if (payments.existsByOrderId(id)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Payments are recorded for this order. Refund is not handled; record the reason and contact the owner");
        }
        String reason = request.reason().trim();
        Map<Long, BigDecimal> used = fabricUse(order.getLines());
        if (!used.isEmpty()) {
            List<Movement> returns = new ArrayList<>();
            used.forEach((productId, metres) -> returns.add(new Movement(productId, TransactionType.ORDER_CANCEL,
                    metres, ReferenceType.ORDER, order.getId(), "Order " + order.getOrderNumber() + " cancelled")));
            inventory.applyAll(returns);
        }
        order.cancel(reason, now());
        history.save(new OrderStatusHistory(order, from, OrderStatus.CANCELLED, now(), currentActor(), reason));
        orders.saveAndFlush(order);
        return detail(order);
    }

    /** Advances (ADVANCE) or instalments (PAYMENT) are appended; the amount may not exceed the balance. */
    @Transactional
    public OrderResponse recordPayment(Long id, PaymentRequest request) {
        CustomerOrder order = lockOrder(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("A cancelled order takes no payments");
        }
        LocalDate today = today();
        LocalDate paidOn = request.paidOn() == null ? today : request.paidOn();
        if (paidOn.isAfter(today)) {
            throw new BusinessException("The payment date cannot be in the future");
        }
        BigDecimal amount = money(request.amount());
        BigDecimal balance = order.getTotal().subtract(paidSum(id));
        if (amount.compareTo(balance) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "The amount is more than the balance due of "
                    + plain(balance));
        }
        payments.saveAndFlush(new OrderPayment(order, request.kind(), amount, paidOn, request.method(),
                trimToNull(request.note()), now(), currentActor()));
        return detail(order);
    }

    /** Due, trial and delivery dates in the range, newest window first by date. Cancelled orders are excluded. */
    @Transactional(readOnly = true)
    public List<CalendarItem> calendar(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("Choose the calendar range");
        }
        if (to.isBefore(from)) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS) {
            throw new BusinessException("The calendar shows at most " + MAX_CALENDAR_DAYS + " days at a time");
        }
        List<CalendarItem> items = new ArrayList<>();
        for (CustomerOrder o : orders.findForCalendar(from, to, OrderStatus.CANCELLED)) {
            addInRange(items, o, o.getDueDate(), CalendarDateType.DUE, from, to);
            addInRange(items, o, o.getTrialDate(), CalendarDateType.TRIAL, from, to);
            addInRange(items, o, o.getDeliveryDate(), CalendarDateType.DELIVERY, from, to);
        }
        items.sort(Comparator.comparing(CalendarItem::date).thenComparing(CalendarItem::orderNumber));
        return items;
    }

    // ------------------------------------------------------------------ lines

    /** Builds the lines (validating each one), copies measurements and sets the server-computed total. */
    private void buildLines(CustomerOrder order, List<OrderLineRequest> requests) {
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 1;
        for (OrderLineRequest r : requests) {
            GarmentType type = types.findById(r.garmentTypeId())
                    .orElseThrow(() -> new BusinessException("Selected garment type does not exist"));
            if (!type.isActive()) {
                throw new BusinessException("Garment type '" + type.getName() + "' is inactive");
            }
            OrderLine line = new OrderLine(order, lineNo, type, r.fabricSource());
            if (r.fabricSource() == FabricSource.STOCK) {
                applyStockFabric(line, r, lineNo);
            } else {
                if (r.fabricProductId() != null || r.fabricMetres() != null) {
                    throw new BusinessException("Garment " + lineNo + ": cloth supplied by the customer cannot "
                            + "also take fabric or metres from stock");
                }
                line.setCustomerFabricNote(trimToNull(r.customerFabricNote()));
            }
            BigDecimal stitching = r.stitchingCharge() == null ? BigDecimal.ZERO : money(r.stitchingCharge());
            line.setStitchingCharge(stitching);
            line.setLineTotal(line.getFabricAmount().add(stitching));
            line.setNotes(trimToNull(r.notes()));
            if (r.measurementRecordId() != null) {
                copyMeasurements(line, order, type, r.measurementRecordId());
            }
            order.addLine(line);
            total = total.add(line.getLineTotal());
            lineNo++;
        }
        order.setTotal(total);
    }

    private void applyStockFabric(OrderLine line, OrderLineRequest r, int lineNo) {
        if (r.fabricProductId() == null) {
            throw new BusinessException("Garment " + lineNo + ": choose the fabric from stock");
        }
        BigDecimal metres = metres(r.fabricMetres());
        if (metres == null || metres.signum() <= 0) {
            throw new BusinessException("Garment " + lineNo + ": enter the metres of fabric");
        }
        Product fabric = products.findById(r.fabricProductId())
                .orElseThrow(() -> new BusinessException("Selected fabric does not exist"));
        if (fabric.getProductType() != ProductType.FABRIC) {
            throw new BusinessException("'" + fabric.getTitle() + "' is not a fabric");
        }
        if (!fabric.isActive()) {
            throw new BusinessException("Fabric '" + fabric.getTitle() + "' is inactive");
        }
        BigDecimal price = fabric.getSellingPrice();
        line.setFabricProduct(fabric);
        line.setFabricName(fabric.getTitle());
        line.setFabricMetres(metres);
        line.setFabricPricePerMetre(price);
        line.setFabricAmount(roundMoney(metres.multiply(price)));
    }

    /** Copies the record's values onto the line; the record must belong to this customer and garment type. */
    private void copyMeasurements(OrderLine line, CustomerOrder order, GarmentType type, Long recordId) {
        MeasurementRecord record = records.findById(recordId)
                .orElseThrow(() -> new BusinessException("Selected measurements do not exist"));
        if (!record.getCustomer().getId().equals(order.getCustomer().getId())) {
            throw new BusinessException("These measurements belong to another customer");
        }
        if (!record.getGarmentType().getId().equals(type.getId())) {
            throw new BusinessException("These measurements are for a different garment type");
        }
        line.setMeasurementRecord(record);
        for (var value : record.getValues()) {
            line.addMeasurement(value.getPointLabel(), value.getValue(), value.getSortOrder());
        }
    }

    /** Fabric metres per product for the STOCK lines (customer-supplied cloth is not counted). */
    private static Map<Long, BigDecimal> fabricUse(List<OrderLine> lines) {
        Map<Long, BigDecimal> use = new LinkedHashMap<>();
        for (OrderLine line : lines) {
            if (line.getFabricSource() == FabricSource.STOCK && line.getFabricProduct() != null) {
                use.merge(line.getFabricProduct().getId(), line.getFabricMetres(), BigDecimal::add);
            }
        }
        return use;
    }

    private static List<Movement> issues(Map<Long, BigDecimal> used, CustomerOrder order, String note) {
        List<Movement> moves = new ArrayList<>();
        used.forEach((productId, metres) -> moves.add(new Movement(productId, TransactionType.ORDER_ISSUE,
                metres.negate(), ReferenceType.ORDER, order.getId(), note)));
        return moves;
    }

    // ------------------------------------------------------------------ helpers

    private void applyHeader(CustomerOrder order, OrderRequest request, LocalDate defaultOrderDate) {
        LocalDate orderDate = request.orderDate() == null ? defaultOrderDate : request.orderDate();
        if (request.dueDate().isBefore(orderDate)) {
            throw new BusinessException("The due date cannot be before the order date");
        }
        order.setOrderDate(orderDate);
        order.setDueDate(request.dueDate());
        order.setTrialDate(request.trialDate());
        order.setDeliveryDate(request.deliveryDate());
        order.setNotes(trimToNull(request.notes()));
    }

    private Customer activeCustomer(Long id) {
        Customer customer = customers.findByIdAndWalkInFalse(id)
                .orElseThrow(() -> new BusinessException("Selected customer does not exist"));
        if (!customer.isActive()) {
            throw new BusinessException("Customer '" + customer.getName() + "' is inactive");
        }
        return customer;
    }

    private CustomerOrder load(Long id) {
        return orders.findWithCustomerById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private CustomerOrder lockOrder(Long id) {
        return orders.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private OrderResponse detail(CustomerOrder order) {
        Ref creator = directory.ref(order.getCreatedBy());
        return OrderMapper.toResponse(order, creator == null ? null : creator.name(),
                payments.findByOrderIdOrderByIdAsc(order.getId()),
                history.findByOrderIdOrderByIdAsc(order.getId()));
    }

    private Map<Long, BigDecimal> paidPerOrder(List<CustomerOrder> list) {
        if (list.isEmpty()) return Map.of();
        List<Long> ids = list.stream().map(CustomerOrder::getId).toList();
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (Object[] row : payments.sumPerOrder(ids)) {
            paid.put((Long) row[0], toDecimal(row[1]));
        }
        return paid;
    }

    private BigDecimal paidSum(Long orderId) {
        return payments.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(OrderPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal toDecimal(Object value) {
        return value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }

    private static void addInRange(List<CalendarItem> items, CustomerOrder o, LocalDate date,
                                   CalendarDateType type, LocalDate from, LocalDate to) {
        if (date != null && !date.isBefore(from) && !date.isAfter(to)) {
            items.add(OrderMapper.calendarItem(o, type, date));
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private AppUser currentActor() {
        return users.getReferenceById(CurrentUser.requireId());
    }
}
