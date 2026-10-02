package com.inventory.repository;

import com.inventory.dto.report.ReportDtos.DocumentRow;
import com.inventory.dto.report.ReportDtos.DocumentSummary;
import com.inventory.dto.report.ReportDtos.Figure;
import com.inventory.dto.report.ReportDtos.InventoryRow;
import com.inventory.dto.report.ReportDtos.InventorySummary;
import com.inventory.dto.report.ReportDtos.LowStockRow;
import com.inventory.dto.report.ReportDtos.LowStockSummary;
import com.inventory.dto.report.ReportDtos.MovementRow;
import com.inventory.dto.report.ReportDtos.MovementSummary;
import com.inventory.dto.report.ReportDtos.RecentDocument;
import com.inventory.dto.report.ReportDtos.RecentMovement;
import com.inventory.dto.report.ReportDtos.RepresentativeRow;
import com.inventory.dto.report.ReportDtos.RepresentativeSummary;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.TransactionType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only reporting queries in plain SQL (aggregates and multi-table joins are clearer and faster
 * here than through JPA). Every filter value is a bound parameter; ORDER BY clauses come only from
 * the whitelists in ReportService, never from user text.
 */
@Repository
public class ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A WHERE clause assembled from optional filters, with its parameters. */
    public static final class Where {
        private final List<String> clauses = new ArrayList<>();
        private final MapSqlParameterSource params = new MapSqlParameterSource();

        public Where add(boolean condition, String clause, String name, Object value) {
            if (condition) {
                clauses.add(clause);
                if (name != null) params.addValue(name, value);
            }
            return this;
        }

        /** Extra parameter used by a clause added earlier. */
        public Where param(boolean condition, String name, Object value) {
            if (condition) params.addValue(name, value);
            return this;
        }

        public Where always(String clause) {
            clauses.add(clause);
            return this;
        }

        String sql() {
            return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        }

        MapSqlParameterSource params() {
            return params;
        }
    }

    public record Paging(int offset, int limit, String orderBy) {
    }

    // ---------------------------------------------------------------- inventory

    private static final String INVENTORY_FROM = """
            FROM inventory i
            JOIN product p ON p.id = i.product_id
            LEFT JOIN publisher pub ON pub.id = p.publisher_id
            LEFT JOIN category cat ON cat.id = p.category_id""";

    public List<InventoryRow> inventoryRows(Where w, Paging page) {
        return jdbc.query("""
                SELECT p.id, p.isbn, p.title, pub.name AS publisher, cat.name AS category, i.quantity, i.reorder_level,
                       p.purchase_price, p.selling_price, i.quantity * p.purchase_price AS stock_value, p.active
                """ + INVENTORY_FROM + w.sql() + " ORDER BY " + page.orderBy() + " LIMIT :limit OFFSET :offset",
                paged(w, page), (rs, n) -> new InventoryRow(rs.getLong("id"), rs.getString("isbn"), rs.getString("title"),
                        rs.getString("publisher"), rs.getString("category"), rs.getInt("quantity"), rs.getInt("reorder_level"),
                        rs.getBigDecimal("purchase_price"), rs.getBigDecimal("selling_price"), rs.getBigDecimal("stock_value"),
                        rs.getBoolean("active")));
    }

    public InventorySummary inventorySummary(Where w) {
        return jdbc.queryForObject("SELECT count(*), coalesce(sum(i.quantity), 0), coalesce(sum(i.quantity * p.purchase_price), 0) "
                        + INVENTORY_FROM + w.sql(), w.params(),
                (rs, n) -> new InventorySummary(rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3)));
    }

    private static final RowMapper<LowStockRow> LOW_STOCK = (rs, n) -> new LowStockRow(rs.getLong("id"), rs.getString("isbn"),
            rs.getString("title"), rs.getString("publisher"), rs.getString("category"), rs.getInt("quantity"),
            rs.getInt("reorder_level"), rs.getInt("shortfall"), rs.getBigDecimal("purchase_price"));

    public List<LowStockRow> lowStockRows(Where w, Paging page) {
        return jdbc.query("""
                SELECT p.id, p.isbn, p.title, pub.name AS publisher, cat.name AS category, i.quantity, i.reorder_level,
                       greatest(i.reorder_level - i.quantity, 0) AS shortfall, p.purchase_price
                """ + INVENTORY_FROM + w.sql() + " ORDER BY " + page.orderBy() + " LIMIT :limit OFFSET :offset",
                paged(w, page), LOW_STOCK);
    }

    public LowStockSummary lowStockSummary(Where w) {
        return jdbc.queryForObject("""
                SELECT count(*), coalesce(sum(CASE WHEN i.quantity = 0 THEN 1 ELSE 0 END), 0),
                       coalesce(sum(greatest(i.reorder_level - i.quantity, 0)), 0)
                """ + INVENTORY_FROM + w.sql(), w.params(),
                (rs, n) -> new LowStockSummary(rs.getLong(1), rs.getLong(2), rs.getLong(3)));
    }

    // ---------------------------------------------------------------- sales / purchases

    /**
     * Table/column names and joins for the two document kinds; constants, never user input.
     * For sales the buyer ("party") is the representative; the customer is optional and shown
     * separately. Invoices from before representatives existed have no representative.
     */
    public enum DocumentKind {
        SALE("sale", "sale_item", "sale_id", "invoice_number", "sale_date",
                " LEFT JOIN representative party ON party.id = d.representative_id"
                        + " LEFT JOIN customer cust ON cust.id = d.customer_id",
                "cust.name", "coalesce(party.name, cust.name)"),
        PURCHASE("purchase", "purchase_item", "purchase_id", "purchase_number", "purchase_date",
                " JOIN supplier party ON party.id = d.supplier_id", "NULL", "party.name");

        final String table, itemTable, itemFk, number, date, joins, customerExpr, buyerExpr;

        DocumentKind(String table, String itemTable, String itemFk, String number, String date, String joins,
                     String customerExpr, String buyerExpr) {
            this.table = table;
            this.itemTable = itemTable;
            this.itemFk = itemFk;
            this.number = number;
            this.date = date;
            this.joins = joins;
            this.customerExpr = customerExpr;
            this.buyerExpr = buyerExpr;
        }

        public String dateColumn() {
            return "d." + date;
        }

        public String numberColumn() {
            return "d." + number;
        }

        public String productExists() {
            return "EXISTS (SELECT 1 FROM " + itemTable + " x WHERE x." + itemFk + " = d.id AND x.product_id = :productId)";
        }
    }

    private static String documentFrom(DocumentKind k) {
        return " FROM " + k.table + " d" + k.joins + " JOIN app_user u ON u.id = d.created_by";
    }

    public List<DocumentRow> documentRows(DocumentKind k, Where w, Paging page) {
        String sql = "SELECT d.id, d." + k.number + " AS number, d." + k.date + " AS doc_date, party.id AS party_id, "
                + "party.name AS party, " + k.customerExpr + " AS customer, "
                + "(SELECT count(*) FROM " + k.itemTable + " x WHERE x." + k.itemFk + " = d.id) AS items, "
                + "d.total, d.payment_status, d.status, u.full_name AS created_by"
                + documentFrom(k) + w.sql() + " ORDER BY " + page.orderBy() + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, paged(w, page), (rs, n) -> new DocumentRow(rs.getLong("id"), rs.getString("number"),
                rs.getObject("doc_date", LocalDate.class), (Long) rs.getObject("party_id"), rs.getString("party"),
                rs.getString("customer"), rs.getInt("items"),
                rs.getBigDecimal("total"), PaymentStatus.valueOf(rs.getString("payment_status")),
                DocumentStatus.valueOf(rs.getString("status")), rs.getString("created_by")));
    }

    public DocumentSummary documentSummary(DocumentKind k, Where w) {
        String completed = "d.status = 'COMPLETED'";
        String sql = "SELECT count(*) FILTER (WHERE " + completed + "), "
                + "coalesce(sum(d.total) FILTER (WHERE " + completed + "), 0), "
                + "coalesce(sum(d.total) FILTER (WHERE " + completed + " AND d.payment_status = 'PAID'), 0), "
                + "coalesce(sum(d.total) FILTER (WHERE " + completed + " AND d.payment_status = 'UNPAID'), 0), "
                + "coalesce(sum(d.total) FILTER (WHERE " + completed + " AND d.payment_status = 'PARTIAL'), 0)"
                + documentFrom(k) + w.sql();
        return jdbc.queryForObject(sql, w.params(), (rs, n) -> new DocumentSummary(rs.getLong(1), rs.getBigDecimal(2),
                rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5)));
    }

    // ---------------------------------------------------------------- stock movement

    /** Human-readable reference: the document number the movement came from. */
    private static final String MOVEMENT_FROM = """
            FROM stock_transaction t
            JOIN product p ON p.id = t.product_id
            JOIN app_user u ON u.id = t.created_by
            LEFT JOIN sale s ON t.reference_type = 'SALE' AND s.id = t.reference_id
            LEFT JOIN purchase pu ON t.reference_type = 'PURCHASE' AND pu.id = t.reference_id
            LEFT JOIN sale_return sr ON t.reference_type = 'SALE_RETURN' AND sr.id = t.reference_id
            LEFT JOIN purchase_return pr ON t.reference_type = 'PURCHASE_RETURN' AND pr.id = t.reference_id
            LEFT JOIN sample_issue smp ON t.reference_type = 'SAMPLE_ISSUE' AND smp.id = t.reference_id
            LEFT JOIN sample_return smr ON t.reference_type = 'SAMPLE_RETURN' AND smr.id = t.reference_id""";

    private static final String REFERENCE_LABEL = """
            CASE t.reference_type
                WHEN 'SALE' THEN s.invoice_number
                WHEN 'PURCHASE' THEN pu.purchase_number
                WHEN 'SALE_RETURN' THEN sr.return_number
                WHEN 'PURCHASE_RETURN' THEN pr.return_number
                WHEN 'ADJUSTMENT' THEN 'ADJ-' || t.reference_id
                WHEN 'SAMPLE_ISSUE' THEN smp.sample_number
                WHEN 'SAMPLE_RETURN' THEN smr.return_number
            END""";

    public List<MovementRow> movementRows(Where w, Paging page) {
        return jdbc.query("SELECT t.id, t.created_at, p.id AS product_id, p.title, p.isbn, t.transaction_type, t.quantity, "
                        + "t.previous_quantity, t.resulting_quantity, t.reference_type, t.reference_id, "
                        + REFERENCE_LABEL + " AS reference, u.full_name, t.notes "
                        + MOVEMENT_FROM + w.sql() + " ORDER BY " + page.orderBy() + " LIMIT :limit OFFSET :offset",
                paged(w, page), (rs, n) -> new MovementRow(rs.getLong("id"), instant(rs, "created_at"), rs.getLong("product_id"),
                        rs.getString("title"), rs.getString("isbn"), TransactionType.valueOf(rs.getString("transaction_type")),
                        rs.getInt("quantity"), rs.getInt("previous_quantity"), rs.getInt("resulting_quantity"),
                        rs.getString("reference_type") == null ? null : ReferenceType.valueOf(rs.getString("reference_type")),
                        (Long) rs.getObject("reference_id"), rs.getString("reference"), rs.getString("full_name"),
                        rs.getString("notes")));
    }

    public MovementSummary movementSummary(Where w) {
        return jdbc.queryForObject("SELECT count(*), coalesce(sum(t.quantity) FILTER (WHERE t.quantity > 0), 0), "
                        + "coalesce(-sum(t.quantity) FILTER (WHERE t.quantity < 0), 0) " + MOVEMENT_FROM + w.sql(), w.params(),
                (rs, n) -> new MovementSummary(rs.getLong(1), rs.getLong(2), rs.getLong(3)));
    }

    // ---------------------------------------------------------------- representatives

    /** Filters for the representative report. */
    public record RepresentativeFilter(LocalDate from, LocalDate to, Long representativeId, String area, Boolean active) {
        boolean narrowsRepresentatives() {
            return representativeId != null || (area != null && !area.isBlank()) || active != null;
        }
    }

    /**
     * One row per representative (+ an "Unassigned" row for invoices/customers without one, shown only
     * when no representative filter is applied, so totals match the Sales report).
     * Sales = COMPLETED invoices dated in the range; returns = sales returns dated in the range.
     */
    private static String representativeRowsSql(RepresentativeFilter f, MapSqlParameterSource p) {
        String saleDates = "";
        String returnDates = "";
        if (f.from() != null) {
            saleDates += " AND d.sale_date >= :from";
            returnDates += " AND x.return_date >= :from";
            p.addValue("from", f.from());
        }
        if (f.to() != null) {
            saleDates += " AND d.sale_date <= :to";
            returnDates += " AND x.return_date <= :to";
            p.addValue("to", f.to());
        }
        List<String> repWhere = new ArrayList<>();
        repWhere.add("TRUE");
        if (f.representativeId() != null) {
            repWhere.add("rep.id = :repId");
            p.addValue("repId", f.representativeId());
        }
        if (f.area() != null && !f.area().isBlank()) {
            repWhere.add("lower(coalesce(rep.area, '')) LIKE :area");
            p.addValue("area", "%" + f.area().trim().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (f.active() != null) {
            repWhere.add("rep.active = :active");
            p.addValue("active", f.active());
        }
        String unassigned = f.narrowsRepresentatives() ? "" : """
                UNION ALL
                SELECT NULL, 'Unassigned', NULL, TRUE,
                       coalesce((SELECT customers FROM c WHERE rid IS NULL), 0),
                       coalesce(s.invoices, 0), coalesce(s.total, 0), coalesce(r.returns, 0),
                       coalesce(s.total, 0) - coalesce(r.returns, 0), coalesce(s.paid, 0), coalesce(s.owed, 0), s.last_sale
                FROM (SELECT 1) one
                LEFT JOIN s ON s.rid IS NULL
                LEFT JOIN r ON r.rid IS NULL
                WHERE coalesce(s.invoices, 0) > 0 OR coalesce(r.returns, 0) > 0
                   OR coalesce((SELECT customers FROM c WHERE rid IS NULL), 0) > 0
                """;
        return """
                WITH s AS (
                    SELECT d.representative_id AS rid, count(*) AS invoices, sum(d.total) AS total,
                           coalesce(sum(d.total) FILTER (WHERE d.payment_status = 'PAID'), 0) AS paid,
                           coalesce(sum(d.total) FILTER (WHERE d.payment_status <> 'PAID'), 0) AS owed,
                           max(d.sale_date) AS last_sale
                    FROM sale d WHERE d.status = 'COMPLETED'""" + saleDates + """
                    GROUP BY d.representative_id),
                r AS (
                    SELECT d.representative_id AS rid, sum(x.total) AS returns
                    FROM sale_return x JOIN sale d ON d.id = x.sale_id WHERE TRUE""" + returnDates + """
                    GROUP BY d.representative_id),
                c AS (
                    SELECT representative_id AS rid, count(*) AS customers FROM customer WHERE NOT walk_in
                    GROUP BY representative_id),
                rows AS (
                    SELECT rep.id, rep.name, rep.area, rep.active, coalesce(c.customers, 0) AS customers,
                           coalesce(s.invoices, 0) AS invoices, coalesce(s.total, 0) AS sales_total,
                           coalesce(r.returns, 0) AS returns, coalesce(s.total, 0) - coalesce(r.returns, 0) AS net_sales,
                           coalesce(s.paid, 0) AS paid, coalesce(s.owed, 0) AS owed, s.last_sale
                    FROM representative rep
                    LEFT JOIN s ON s.rid = rep.id
                    LEFT JOIN r ON r.rid = rep.id
                    LEFT JOIN c ON c.rid = rep.id
                    WHERE\s""" + String.join(" AND ", repWhere) + "\n" + unassigned + ")\n";
    }

    public List<RepresentativeRow> representativeRows(RepresentativeFilter f, Paging page) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = representativeRowsSql(f, p) + "SELECT * FROM rows ORDER BY " + page.orderBy()
                + " LIMIT :limit OFFSET :offset";
        p.addValue("limit", page.limit()).addValue("offset", page.offset());
        return jdbc.query(sql, p, (rs, n) -> new RepresentativeRow((Long) rs.getObject("id"), rs.getString("name"),
                rs.getString("area"), rs.getBoolean("active"), rs.getLong("customers"), rs.getLong("invoices"),
                rs.getBigDecimal("sales_total"), rs.getBigDecimal("returns"), rs.getBigDecimal("net_sales"),
                rs.getBigDecimal("paid"), rs.getBigDecimal("owed"), rs.getObject("last_sale", LocalDate.class)));
    }

    public RepresentativeSummary representativeSummary(RepresentativeFilter f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = representativeRowsSql(f, p) + """
                SELECT count(*), count(id), coalesce(sum(invoices), 0), coalesce(sum(sales_total), 0),
                       coalesce(sum(returns), 0), coalesce(sum(net_sales), 0), coalesce(sum(owed), 0)
                FROM rows""";
        return jdbc.queryForObject(sql, p, (rs, n) -> new RepresentativeSummary(rs.getLong(1), rs.getLong(2),
                rs.getLong(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7)));
    }

    // ---------------------------------------------------------------- counts & dashboard

    public long count(String fromClause, Where w) {
        Long n = jdbc.queryForObject("SELECT count(*) " + fromClause + w.sql(), w.params(), Long.class);
        return n == null ? 0 : n;
    }

    public static String inventoryFrom() {
        return INVENTORY_FROM;
    }

    public static String documentFromClause(DocumentKind k) {
        return documentFrom(k);
    }

    public static String movementFrom() {
        return MOVEMENT_FROM;
    }

    /** Completed documents and their total between two dates (inclusive). */
    public Figure documentFigure(DocumentKind k, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("SELECT count(*), coalesce(sum(total), 0) FROM " + k.table
                        + " WHERE status = 'COMPLETED' AND " + k.date + " BETWEEN :from AND :to",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new Figure(rs.getLong(1), rs.getBigDecimal(2)));
    }

    public List<RecentDocument> recentDocuments(DocumentKind k, int limit) {
        return jdbc.query("SELECT d.id, d." + k.number + " AS number, d." + k.date + " AS doc_date, " + k.buyerExpr + " AS party, "
                        + "d.total, d.status FROM " + k.table + " d" + k.joins
                        + " ORDER BY d.created_at DESC, d.id DESC LIMIT :limit",
                new MapSqlParameterSource("limit", limit),
                (rs, n) -> new RecentDocument(rs.getLong("id"), rs.getString("number"), rs.getObject("doc_date", LocalDate.class),
                        rs.getString("party"), rs.getBigDecimal("total"), DocumentStatus.valueOf(rs.getString("status"))));
    }

    public List<RecentMovement> recentMovements(int limit) {
        return jdbc.query("""
                        SELECT t.id, t.created_at, p.title, t.transaction_type, t.quantity, u.full_name
                        FROM stock_transaction t JOIN product p ON p.id = t.product_id JOIN app_user u ON u.id = t.created_by
                        ORDER BY t.created_at DESC, t.id DESC LIMIT :limit""",
                new MapSqlParameterSource("limit", limit),
                (rs, n) -> new RecentMovement(rs.getLong("id"), instant(rs, "created_at"), rs.getString("title"),
                        TransactionType.valueOf(rs.getString("transaction_type")), rs.getInt("quantity"), rs.getString("full_name")));
    }

    public List<LowStockRow> topLowStock(int limit) {
        return jdbc.query("""
                        SELECT p.id, p.isbn, p.title, pub.name AS publisher, cat.name AS category, i.quantity, i.reorder_level,
                               greatest(i.reorder_level - i.quantity, 0) AS shortfall, p.purchase_price
                        """ + INVENTORY_FROM + """
                         WHERE p.active AND i.quantity <= i.reorder_level
                        ORDER BY i.quantity ASC, (i.reorder_level - i.quantity) DESC, p.title ASC LIMIT :limit""",
                new MapSqlParameterSource("limit", limit), LOW_STOCK);
    }

    // ---------------------------------------------------------------- helpers

    private static MapSqlParameterSource paged(Where w, Paging page) {
        return new MapSqlParameterSource(w.params().getValues())
                .addValue("limit", page.limit())
                .addValue("offset", page.offset());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.time.OffsetDateTime odt = rs.getObject(column, java.time.OffsetDateTime.class);
        return odt == null ? null : odt.toInstant();
    }
}
