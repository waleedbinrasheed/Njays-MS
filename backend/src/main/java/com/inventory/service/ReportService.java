package com.inventory.service;

import com.inventory.dto.report.ReportDtos.FabricStockReport;
import com.inventory.dto.report.ReportDtos.FabricStockRow;
import com.inventory.dto.report.ReportDtos.FabricStockTotals;
import com.inventory.dto.report.ReportDtos.OrderReport;
import com.inventory.dto.report.ReportDtos.OrderReportRow;
import com.inventory.dto.report.ReportDtos.OrderReportTotals;
import com.inventory.entity.CustomerOrder;
import com.inventory.entity.Inventory;
import com.inventory.entity.OrderStatus;
import com.inventory.exception.BusinessException;
import com.inventory.mapper.OrderMapper;
import com.inventory.repository.CustomerOrderRepository;
import com.inventory.repository.InventoryRepository;
import com.inventory.repository.OrderPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.inventory.service.MasterDataSupport.roundMoney;
import static com.inventory.service.MasterDataSupport.searchTerm;

/** Orders report and fabric stock report. Totals cover every matching row, not just one page. */
@Service
public class ReportService {

    private final CustomerOrderRepository orders;
    private final OrderPaymentRepository payments;
    private final InventoryRepository inventories;

    public ReportService(CustomerOrderRepository orders, OrderPaymentRepository payments, InventoryRepository inventories) {
        this.orders = orders;
        this.payments = payments;
        this.inventories = inventories;
    }

    /**
     * Orders by order date. Cancelled orders are listed (so nothing disappears silently) but are not
     * counted in the totals.
     */
    @Transactional(readOnly = true)
    public OrderReport orders(LocalDate from, LocalDate to, OrderStatus status, Long customerId) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        List<CustomerOrder> list = orders.findForReport(from, to, status, customerId);
        Map<Long, BigDecimal> paid = paidPerOrder(list);

        List<OrderReportRow> rows = list.stream().map(o -> {
            BigDecimal amountPaid = paid.getOrDefault(o.getId(), BigDecimal.ZERO);
            return new OrderReportRow(o.getId(), o.getOrderNumber(), o.getOrderDate(), o.getCustomer().getName(),
                    o.getStatus(), o.getDueDate(), o.getTotal(), amountPaid, o.getTotal().subtract(amountPaid),
                    OrderMapper.paymentStatus(o.getTotal(), amountPaid));
        }).toList();

        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (OrderReportRow row : rows) {
            if (row.status() == OrderStatus.CANCELLED) continue;
            count++;
            total = total.add(row.total());
            totalPaid = totalPaid.add(row.paid());
        }
        return new OrderReport(rows, new OrderReportTotals(count, total, totalPaid, total.subtract(totalPaid)));
    }

    /** Active fabrics with their stock, sorted by code. {@code costValue} = metres on hand x cost per metre. */
    @Transactional(readOnly = true)
    public FabricStockReport fabricStock(String search, boolean lowStockOnly) {
        String term = searchTerm(search);
        List<FabricStockRow> rows = inventories.findAllFabricStock().stream()
                .filter(i -> i.getProduct().isActive())
                .filter(i -> term.isEmpty() || matches(i, term))
                .filter(i -> !lowStockOnly || i.isLowStock())
                .sorted(Comparator.comparing((Inventory i) -> i.getProduct().getFabricCode(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(i -> new FabricStockRow(
                        i.getProduct().getId(),
                        i.getProduct().getFabricCode(),
                        i.getProduct().getTitle(),
                        i.getProduct().getColour(),
                        i.getQuantity(),
                        i.getReorderLevel(),
                        i.isLowStock(),
                        i.getProduct().getPurchasePrice(),
                        roundMoney(i.getQuantity().multiply(i.getProduct().getPurchasePrice()))))
                .toList();
        BigDecimal metres = rows.stream().map(FabricStockRow::quantityOnHand).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = rows.stream().map(FabricStockRow::costValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FabricStockReport(rows, new FabricStockTotals(rows.size(), metres, value));
    }

    private static boolean matches(Inventory i, String term) {
        String lower = term.toLowerCase(Locale.ROOT);
        return contains(i.getProduct().getTitle(), lower) || contains(i.getProduct().getFabricCode(), lower)
                || contains(i.getProduct().getColour(), lower) || contains(i.getProduct().getMaterial(), lower);
    }

    private static boolean contains(String value, String lowerTerm) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerTerm);
    }

    private Map<Long, BigDecimal> paidPerOrder(List<CustomerOrder> list) {
        if (list.isEmpty()) return Map.of();
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (Object[] row : payments.sumPerOrder(list.stream().map(CustomerOrder::getId).toList())) {
            paid.put((Long) row[0], row[1] instanceof BigDecimal b ? b : new BigDecimal(row[1].toString()));
        }
        return paid;
    }
}
