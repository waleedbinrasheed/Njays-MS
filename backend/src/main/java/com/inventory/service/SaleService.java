package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.document.DocumentDtos.CancelRequest;
import com.inventory.dto.document.DocumentDtos.PaymentStatusRequest;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.sale.SaleDtos.CreateSaleRequest;
import com.inventory.dto.sale.SaleDtos.SaleDetail;
import com.inventory.dto.sale.SaleDtos.SaleLineRequest;
import com.inventory.dto.sale.SaleDtos.SaleSummary;
import com.inventory.entity.Customer;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.Product;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.Sale;
import com.inventory.entity.SaleItem;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.SaleMapper;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.SaleReturnRepository;
import com.inventory.repository.CustomerRepository;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.SaleRepository;
import com.inventory.repository.SaleRepository.Filters;
import com.inventory.security.CurrentUser;
import com.inventory.service.DocumentNumberService.DocumentType;
import com.inventory.service.DocumentPricing.LineAmounts;
import com.inventory.service.DocumentPricing.LineInput;
import com.inventory.service.DocumentPricing.Totals;
import com.inventory.service.InventoryService.Movement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Sales invoices. Saving the invoice and taking its stock happen in ONE transaction; the stock
 * check is made by {@link InventoryService} while each product's stock row is locked, so two
 * cashiers can never sell the same last copy.
 */
@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private final SaleRepository sales;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final AppUserRepository users;
    private final InventoryService inventoryService;
    private final DocumentNumberService numbers;
    private final UserDirectory userDirectory;
    private final SaleReturnRepository saleReturns;
    private final Clock clock;
    private final ZoneId zone;

    public SaleService(SaleRepository sales, CustomerRepository customers, ProductRepository products,
                       AppUserRepository users, InventoryService inventoryService, DocumentNumberService numbers,
                       UserDirectory userDirectory, SaleReturnRepository saleReturns,
                       Clock clock, ZoneId businessZone) {
        this.sales = sales;
        this.customers = customers;
        this.products = products;
        this.users = users;
        this.inventoryService = inventoryService;
        this.numbers = numbers;
        this.userDirectory = userDirectory;
        this.saleReturns = saleReturns;
        this.clock = clock;
        this.zone = businessZone;
    }

    public record SaleFilter(String search, Long customerId, Long productId, LocalDate from, LocalDate to,
                             PaymentStatus paymentStatus, DocumentStatus status, Long createdBy) {
    }

    @Transactional
    public SaleDetail create(CreateSaleRequest request) {
        if (request.saleDate().isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw new BusinessException("Sale date cannot be in the future");
        }
        Customer customer = resolveCustomer(request.customerId());
        Map<Long, Product> productsById = loadProducts(request.items());

        Sale sale = new Sale(numbers.next(DocumentType.SALE), customer, request.saleDate(), request.paymentStatus(),
                trimToNull(request.notes()));

        List<LineAmounts> amounts = new ArrayList<>();
        for (SaleLineRequest line : request.items()) {
            LineAmounts a = DocumentPricing.line(new LineInput(line.quantity(), line.unitPrice(),
                    line.discountPercent(), line.taxPercent()));
            amounts.add(a);
            sale.addItem(new SaleItem(productsById.get(line.productId()), line.quantity(), line.unitPrice(),
                    DocumentPricing.orZero(line.discountPercent()), a.discount(),
                    DocumentPricing.orZero(line.taxPercent()), a.tax(), a.total()));
        }
        Totals totals = DocumentPricing.totals(amounts, request.invoiceDiscount());
        sale.setTotals(totals.subtotal(), totals.invoiceDiscount(), totals.discountTotal(), totals.taxTotal(),
                totals.total());

        Sale saved = sales.saveAndFlush(sale);

        // Throws "Insufficient stock for '...': available X, requested Y" and rolls everything back.
        inventoryService.applyAll(saved.getItems().stream()
                .map(i -> new Movement(i.getProduct().getId(), TransactionType.SALE, -i.getQuantity(),
                        ReferenceType.SALE, saved.getId(), saved.getInvoiceNumber()))
                .toList());

        log.info("Sale {} saved: {} lines, total {}", saved.getInvoiceNumber(), saved.getItems().size(), saved.getTotal());
        return detail(saved);
    }

    /**
     * Returns all sold stock. Refused once the invoice has returns (use a return for the rest instead),
     * so stock can never be given back twice.
     */
    @Transactional
    public SaleDetail cancel(Long id, CancelRequest request) {
        Sale sale = sales.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Sale", id));
        if (sale.isCancelled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Invoice " + sale.getInvoiceNumber() + " is already cancelled");
        }
        if (saleReturns.existsBySaleId(sale.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Invoice " + sale.getInvoiceNumber()
                    + " has returns and cannot be cancelled. Record a return for the remaining items instead");
        }
        String reason = request.reason().trim();
        inventoryService.applyAll(sale.getItems().stream()
                .map(i -> new Movement(i.getProduct().getId(), TransactionType.SALE_CANCEL, i.getQuantity(),
                        ReferenceType.SALE, sale.getId(), "Cancelled " + sale.getInvoiceNumber() + ": " + reason))
                .toList());
        sale.cancel(users.getReferenceById(CurrentUser.requireId()), Instant.now(clock), reason);
        log.info("Sale {} cancelled by user id {}", sale.getInvoiceNumber(), CurrentUser.requireId());
        return detail(sales.saveAndFlush(sale));
    }

    @Transactional
    public SaleDetail updatePaymentStatus(Long id, PaymentStatusRequest request) {
        Sale sale = sales.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Sale", id));
        if (sale.isCancelled()) {
            throw new BusinessException("Cannot change the payment status of a cancelled invoice");
        }
        sale.setPaymentStatus(request.paymentStatus());
        return detail(sales.saveAndFlush(sale));
    }

    @Transactional(readOnly = true)
    public SaleDetail get(Long id) {
        return detail(sales.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Sale", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleSummary> search(SaleFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Specification<Sale> spec = Specification.allOf(
                Filters.matches(f.search()), Filters.customer(f.customerId()), Filters.product(f.productId()),
                Filters.dateFrom(f.from()), Filters.dateTo(f.to()), Filters.paymentStatus(f.paymentStatus()),
                Filters.status(f.status()), Filters.createdBy(f.createdBy()));
        Page<Sale> page = sales.findAll(spec, pageable);
        Map<Long, Ref> creators = userDirectory.refs(page.getContent().stream().map(Sale::getCreatedBy).toList());
        return PageResponse.of(page, s -> SaleMapper.toSummary(s, creators.get(s.getCreatedBy())));
    }

    /** Called inside a transaction, so lazy lines/products load as needed. */
    private SaleDetail detail(Sale s) {
        return SaleMapper.toDetail(s, userDirectory.ref(s.getCreatedBy()));
    }

    private Customer resolveCustomer(Long customerId) {
        Customer customer = customerId == null
                ? customers.findByWalkInTrue().orElseThrow(() -> new IllegalStateException("Walk-in customer missing"))
                : customers.findById(customerId).orElseThrow(() -> new BusinessException("Selected customer does not exist"));
        if (!customer.isActive()) {
            throw new BusinessException("Customer '" + customer.getName() + "' is inactive");
        }
        return customer;
    }

    /** All products must exist, be active, and appear on only one line. */
    private Map<Long, Product> loadProducts(List<SaleLineRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (SaleLineRequest line : items) {
            if (!seen.add(line.productId())) {
                throw new BusinessException("The same product appears on more than one line; combine them into one line");
            }
        }
        Map<Long, Product> byId = products.findAllById(seen).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (SaleLineRequest line : items) {
            Product product = byId.get(line.productId());
            if (product == null) {
                throw new BusinessException("Product " + line.productId() + " does not exist");
            }
            if (!product.isActive()) {
                throw new BusinessException("Product '" + product.getTitle() + "' is inactive and cannot be sold");
            }
        }
        return byId;
    }
}
