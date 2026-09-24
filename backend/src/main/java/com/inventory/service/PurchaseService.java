package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.document.DocumentDtos.CancelRequest;
import com.inventory.dto.purchase.PurchaseDtos.CreatePurchaseRequest;
import com.inventory.dto.document.DocumentDtos.PaymentStatusRequest;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseDetail;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseLineRequest;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseSummary;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.Product;
import com.inventory.entity.Purchase;
import com.inventory.entity.PurchaseItem;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.Supplier;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.PurchaseMapper;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.PurchaseReturnRepository;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.PurchaseRepository;
import com.inventory.repository.PurchaseRepository.Filters;
import com.inventory.repository.SupplierRepository;
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
 * Purchases (goods received). Saving a purchase and adding its stock happen in ONE transaction:
 * if anything fails, neither the purchase, its number, nor any stock change is kept.
 */
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final PurchaseRepository purchases;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final AppUserRepository users;
    private final InventoryService inventoryService;
    private final DocumentNumberService numbers;
    private final UserDirectory userDirectory;
    private final PurchaseReturnRepository purchaseReturns;
    private final Clock clock;
    private final ZoneId zone;

    public PurchaseService(PurchaseRepository purchases, SupplierRepository suppliers, ProductRepository products,
                           AppUserRepository users, InventoryService inventoryService, DocumentNumberService numbers,
                           UserDirectory userDirectory, PurchaseReturnRepository purchaseReturns,
                           Clock clock, ZoneId businessZone) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.products = products;
        this.users = users;
        this.inventoryService = inventoryService;
        this.numbers = numbers;
        this.userDirectory = userDirectory;
        this.purchaseReturns = purchaseReturns;
        this.clock = clock;
        this.zone = businessZone;
    }

    public record PurchaseFilter(String search, Long supplierId, Long productId, LocalDate from, LocalDate to,
                                 PaymentStatus paymentStatus, DocumentStatus status) {
    }

    @Transactional
    public PurchaseDetail create(CreatePurchaseRequest request) {
        if (request.purchaseDate().isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw new BusinessException("Purchase date cannot be in the future");
        }
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new BusinessException("Selected supplier does not exist"));
        if (!supplier.isActive()) {
            throw new BusinessException("Supplier '" + supplier.getName() + "' is inactive");
        }
        Map<Long, Product> productsById = loadProducts(request.items());

        Purchase purchase = new Purchase(numbers.next(DocumentType.PURCHASE), supplier,
                trimToNull(request.supplierInvoiceNumber()), request.purchaseDate(), request.paymentStatus(),
                trimToNull(request.notes()));

        List<LineAmounts> amounts = new ArrayList<>();
        for (PurchaseLineRequest line : request.items()) {
            LineAmounts a = DocumentPricing.line(new LineInput(line.quantity(), line.unitCost(),
                    line.discountPercent(), line.taxPercent()));
            amounts.add(a);
            purchase.addItem(new PurchaseItem(productsById.get(line.productId()), line.quantity(), line.unitCost(),
                    DocumentPricing.orZero(line.discountPercent()), a.discount(),
                    DocumentPricing.orZero(line.taxPercent()), a.tax(), a.total()));
        }
        Totals totals = DocumentPricing.totals(amounts, request.invoiceDiscount());
        purchase.setTotals(totals.subtotal(), totals.invoiceDiscount(), totals.discountTotal(), totals.taxTotal(),
                totals.total());

        Purchase saved = purchases.saveAndFlush(purchase);

        inventoryService.applyAll(saved.getItems().stream()
                .map(i -> new Movement(i.getProduct().getId(), TransactionType.PURCHASE, i.getQuantity(),
                        ReferenceType.PURCHASE, saved.getId(), saved.getPurchaseNumber()))
                .toList());

        log.info("Purchase {} saved: {} lines, total {}", saved.getPurchaseNumber(), saved.getItems().size(), saved.getTotal());
        return detail(saved);
    }

    /**
     * Reverses all stock the purchase added. Refused if some of it has already left the shop,
     * because the reversal would take stock below zero.
     */
    @Transactional
    public PurchaseDetail cancel(Long id, CancelRequest request) {
        Purchase purchase = purchases.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase", id));
        if (purchase.isCancelled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Purchase " + purchase.getPurchaseNumber() + " is already cancelled");
        }
        if (purchaseReturns.existsByPurchaseId(purchase.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Purchase " + purchase.getPurchaseNumber()
                    + " has returns and cannot be cancelled. Record a return for the remaining items instead");
        }
        String reason = request.reason().trim();
        try {
            inventoryService.applyAll(purchase.getItems().stream()
                    .map(i -> new Movement(i.getProduct().getId(), TransactionType.PURCHASE_CANCEL, -i.getQuantity(),
                            ReferenceType.PURCHASE, purchase.getId(), "Cancelled " + purchase.getPurchaseNumber() + ": " + reason))
                    .toList());
        } catch (BusinessException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "Cannot cancel " + purchase.getPurchaseNumber()
                    + " because some of its stock has already been used. " + ex.getMessage());
        }
        purchase.cancel(users.getReferenceById(CurrentUser.requireId()), Instant.now(clock), reason);
        log.info("Purchase {} cancelled by user id {}", purchase.getPurchaseNumber(), CurrentUser.requireId());
        return detail(purchases.saveAndFlush(purchase));
    }

    /** The only change allowed on a saved purchase: it does not affect stock or amounts. */
    @Transactional
    public PurchaseDetail updatePaymentStatus(Long id, PaymentStatusRequest request) {
        Purchase purchase = purchases.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase", id));
        if (purchase.isCancelled()) {
            throw new BusinessException("Cannot change the payment status of a cancelled purchase");
        }
        purchase.setPaymentStatus(request.paymentStatus());
        return detail(purchases.saveAndFlush(purchase));
    }

    @Transactional(readOnly = true)
    public PurchaseDetail get(Long id) {
        return detail(purchases.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummary> search(PurchaseFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Specification<Purchase> spec = Specification.allOf(
                Filters.matches(f.search()), Filters.supplier(f.supplierId()), Filters.product(f.productId()),
                Filters.dateFrom(f.from()), Filters.dateTo(f.to()),
                Filters.paymentStatus(f.paymentStatus()), Filters.status(f.status()));
        Page<Purchase> page = purchases.findAll(spec, pageable);
        Map<Long, Ref> creators = userDirectory.refs(page.getContent().stream().map(Purchase::getCreatedBy).toList());
        return PageResponse.of(page, p -> PurchaseMapper.toSummary(p, creators.get(p.getCreatedBy())));
    }

    /** Called inside a transaction, so lazy lines/products load as needed. */
    private PurchaseDetail detail(Purchase p) {
        return PurchaseMapper.toDetail(p, userDirectory.ref(p.getCreatedBy()));
    }

    /** All products must exist, be active, and appear on only one line. */
    private Map<Long, Product> loadProducts(List<PurchaseLineRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (PurchaseLineRequest line : items) {
            if (!seen.add(line.productId())) {
                throw new BusinessException("The same product appears on more than one line; combine them into one line");
            }
        }
        Map<Long, Product> byId = products.findAllById(seen).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (PurchaseLineRequest line : items) {
            Product product = byId.get(line.productId());
            if (product == null) {
                throw new BusinessException("Product " + line.productId() + " does not exist");
            }
            if (!product.isActive()) {
                throw new BusinessException("Product '" + product.getTitle() + "' is inactive and cannot be purchased");
            }
        }
        return byId;
    }
}
