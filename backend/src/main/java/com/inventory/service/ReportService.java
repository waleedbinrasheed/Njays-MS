package com.inventory.service;

import com.inventory.dto.report.ReportDtos.DocumentRow;
import com.inventory.dto.report.ReportDtos.DocumentSummary;
import com.inventory.dto.report.ReportDtos.InventoryRow;
import com.inventory.dto.report.ReportDtos.InventorySummary;
import com.inventory.dto.report.ReportDtos.LowStockRow;
import com.inventory.dto.report.ReportDtos.LowStockSummary;
import com.inventory.dto.report.ReportDtos.MovementRow;
import com.inventory.dto.report.ReportDtos.MovementSummary;
import com.inventory.dto.report.ReportDtos.ReportPage;
import com.inventory.dto.report.ReportDtos.RepresentativeRow;
import com.inventory.dto.report.ReportDtos.RepresentativeSummary;
import com.inventory.repository.ReportRepository.RepresentativeFilter;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.repository.InventorySpecifications.StockStatus;
import com.inventory.repository.ReportRepository;
import com.inventory.repository.ReportRepository.DocumentKind;
import com.inventory.repository.ReportRepository.Paging;
import com.inventory.repository.ReportRepository.Where;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The five reports. Totals always cover every matching row, not just the current page. */
@Service
public class ReportService {

    private static final Map<String, String> INVENTORY_SORT = Map.of(
            "title", "p.title", "isbn", "p.isbn", "publisher", "pub.name", "category", "cat.name",
            "quantity", "i.quantity", "reorderLevel", "i.reorder_level", "stockValue", "stock_value",
            "sellingPrice", "p.selling_price", "purchasePrice", "p.purchase_price");
    private static final Map<String, String> LOW_STOCK_SORT = Map.of(
            "title", "p.title", "quantity", "i.quantity", "reorderLevel", "i.reorder_level", "shortfall", "shortfall",
            "category", "cat.name", "publisher", "pub.name");
    private static final Map<String, String> MOVEMENT_SORT = Map.of("date", "t.created_at", "product", "p.title",
            "quantity", "t.quantity");

    private final ReportRepository reports;
    private final ZoneId zone;

    public ReportService(ReportRepository reports, ZoneId businessZone) {
        this.reports = reports;
        this.zone = businessZone;
    }

    public record InventoryFilter(String search, Long productId, Long categoryId, Long publisherId, StockStatus stock,
                                  Boolean active) {
    }

    /**
     * {@code partyId}: customer for sales, supplier for purchases. {@code representativeId}: sales only.
     */
    public record DocumentFilter(LocalDate from, LocalDate to, Long partyId, Long representativeId, Long productId,
                                 PaymentStatus paymentStatus, DocumentStatus status) {
    }

    public record MovementFilter(Long productId, TransactionType type, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public ReportPage<InventoryRow, InventorySummary> inventory(InventoryFilter f, Pageable pageable) {
        Where w = inventoryWhere(f);
        Paging paging = paging(pageable, INVENTORY_SORT, "p.title ASC", "p.id");
        InventorySummary summary = reports.inventorySummary(w);
        return page(reports.inventoryRows(w, paging), summary.products(), pageable, summary);
    }

    @Transactional(readOnly = true)
    public ReportPage<LowStockRow, LowStockSummary> lowStock(Long categoryId, Long publisherId, Pageable pageable) {
        Where w = new Where()
                .always("p.active")
                .always("i.quantity <= i.reorder_level")
                .add(categoryId != null, "p.category_id = :categoryId", "categoryId", categoryId)
                .add(publisherId != null, "p.publisher_id = :publisherId", "publisherId", publisherId);
        Paging paging = paging(pageable, LOW_STOCK_SORT, "i.quantity ASC, shortfall DESC, p.title ASC", "p.id");
        LowStockSummary summary = reports.lowStockSummary(w);
        return page(reports.lowStockRows(w, paging), summary.products(), pageable, summary);
    }

    @Transactional(readOnly = true)
    public ReportPage<DocumentRow, DocumentSummary> documents(DocumentKind kind, DocumentFilter f, Pageable pageable) {
        checkRange(f.from(), f.to());
        Where w = new Where()
                .add(f.from() != null, kind.dateColumn() + " >= :from", "from", f.from())
                .add(f.to() != null, kind.dateColumn() + " <= :to", "to", f.to())
                .add(f.partyId() != null, (kind == DocumentKind.SALE ? "d.customer_id" : "d.supplier_id") + " = :partyId",
                        "partyId", f.partyId())
                .add(f.representativeId() != null && kind == DocumentKind.SALE, "d.representative_id = :repId", "repId",
                        f.representativeId())
                .add(f.productId() != null, kind.productExists(), "productId", f.productId())
                .add(f.paymentStatus() != null, "d.payment_status = :paymentStatus", "paymentStatus",
                        f.paymentStatus() == null ? null : f.paymentStatus().name())
                .add(f.status() != null, "d.status = :status", "status", f.status() == null ? null : f.status().name());
        Map<String, String> sort = Map.of("date", kind.dateColumn(), "number", kind.numberColumn(), "total", "d.total",
                "party", "party.name", "customer", kind == DocumentKind.SALE ? "cust.name" : "party.name");
        Paging paging = paging(pageable, sort, kind.dateColumn() + " DESC", "d.id");
        long total = reports.count(ReportRepository.documentFromClause(kind), w);
        return page(reports.documentRows(kind, w, paging), total, pageable, reports.documentSummary(kind, w));
    }

    private static final Map<String, String> REPRESENTATIVE_SORT = Map.of(
            "name", "name", "area", "area", "customers", "customers", "invoices", "invoices",
            "salesTotal", "sales_total", "returns", "returns", "netSales", "net_sales", "owed", "owed",
            "lastSale", "last_sale");

    @Transactional(readOnly = true)
    public ReportPage<RepresentativeRow, RepresentativeSummary> representatives(RepresentativeFilter f, Pageable pageable) {
        checkRange(f.from(), f.to());
        Paging paging = paging(pageable, REPRESENTATIVE_SORT, "net_sales DESC, name ASC", "id");
        RepresentativeSummary summary = reports.representativeSummary(f);
        return page(reports.representativeRows(f, paging), summary.rows(), pageable, summary);
    }

    @Transactional(readOnly = true)
    public ReportPage<MovementRow, MovementSummary> movements(MovementFilter f, Pageable pageable) {
        checkRange(f.from(), f.to());
        Where w = new Where()
                .add(f.productId() != null, "t.product_id = :productId", "productId", f.productId())
                .add(f.type() != null, "t.transaction_type = :type", "type", f.type() == null ? null : f.type().name())
                .add(f.from() != null, "t.created_at >= :fromTs", "fromTs", startOf(f.from()))
                .add(f.to() != null, "t.created_at < :toTs", "toTs", startOf(f.to() == null ? null : f.to().plusDays(1)));
        Paging paging = paging(pageable, MOVEMENT_SORT, "t.created_at DESC", "t.id");
        MovementSummary summary = reports.movementSummary(w);
        return page(reports.movementRows(w, paging), summary.movements(), pageable, summary);
    }

    // ------------------------------------------------------------------ helpers

    private Where inventoryWhere(InventoryFilter f) {
        String term = f.search() == null || f.search().isBlank() ? null : "%" + f.search().trim().toLowerCase(Locale.ROOT) + "%";
        String isbn = term == null ? null : "%" + f.search().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT) + "%";
        Where w = new Where()
                .add(term != null, "(lower(p.title) LIKE :term OR lower(coalesce(p.author, '')) LIKE :term OR p.isbn LIKE :isbn)",
                        "term", term)
                .param(term != null, "isbn", isbn)
                .add(f.productId() != null, "p.id = :productId", "productId", f.productId())
                .add(f.categoryId() != null, "p.category_id = :categoryId", "categoryId", f.categoryId())
                .add(f.publisherId() != null, "p.publisher_id = :publisherId", "publisherId", f.publisherId())
                .add(f.active() != null, "p.active = :active", "active", f.active());
        if (f.stock() == StockStatus.LOW) w.always("i.quantity <= i.reorder_level");
        if (f.stock() == StockStatus.OUT) w.always("i.quantity = 0");
        if (f.stock() == StockStatus.IN_STOCK) w.always("i.quantity > 0");
        return w;
    }

    /** Translates the requested sort through a whitelist into SQL, always ending with a unique tie-breaker. */
    static Paging paging(Pageable pageable, Map<String, String> allowed, String defaultOrder, String idColumn) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) {
            String column = allowed.get(o.getProperty());
            if (column == null) {
                throw new BusinessException("Cannot sort by '" + o.getProperty() + "'");
            }
            parts.add(column + (o.isAscending() ? " ASC" : " DESC") + " NULLS LAST");
        }
        String order = parts.isEmpty() ? defaultOrder : String.join(", ", parts);
        return new Paging((int) pageable.getOffset(), pageable.getPageSize(), order + ", " + idColumn + " DESC");
    }

    static <T, S> ReportPage<T, S> page(List<T> rows, long total, Pageable pageable, S summary) {
        int size = pageable.getPageSize();
        int pages = size == 0 ? 0 : (int) ((total + size - 1) / size);
        return new ReportPage<>(rows, pageable.getPageNumber(), size, total, pages, summary);
    }

    static void checkRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
    }

    private java.time.OffsetDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(zone).toOffsetDateTime();
    }
}
