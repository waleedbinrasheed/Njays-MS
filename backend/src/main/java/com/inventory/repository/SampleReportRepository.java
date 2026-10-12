package com.inventory.repository;

import com.inventory.dto.sample.SampleDtos.SampleLineRow;
import com.inventory.dto.sample.SampleDtos.SampleReportSummary;
import com.inventory.dto.sample.SampleDtos.SampleRepresentativeRow;
import com.inventory.entity.SampleStatus;
import com.inventory.repository.ReportRepository.Paging;
import com.inventory.repository.ReportRepository.Where;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Sample report queries (plain SQL, bound parameters, ORDER BY only from whitelists). Everything is
 * per issued line: issued = line, returned = sum of its return lines, outstanding = issued - returned
 * (0 for cancelled samples). Cancelled samples are listed in the detail view but never counted.
 */
@Repository
public class SampleReportRepository {

    /** Filters shared by both views; the date range is the sample's issue date. */
    public record SampleReportFilter(Long representativeId, Long customerId, Long productId, LocalDate from,
                                     LocalDate to, SampleStatus status) {
    }

    private static final String FROM = """
             FROM sample_issue_item it
            JOIN sample_issue si ON si.id = it.sample_issue_id
            JOIN representative r ON r.id = si.representative_id
            JOIN product p ON p.id = it.product_id
            LEFT JOIN (SELECT sample_issue_item_id, sum(quantity) AS qty, sum(line_total) AS amount
                       FROM sample_return_item GROUP BY sample_issue_item_id) ret ON ret.sample_issue_item_id = it.id""";

    private static final String RETURNED_QTY = "coalesce(ret.qty, 0)";
    private static final String RETURNED_AMOUNT = "coalesce(ret.amount, 0)";
    private static final String OUT_QTY = "CASE WHEN si.status = 'CANCELLED' THEN 0 ELSE it.quantity - " + RETURNED_QTY + " END";
    private static final String OUT_AMOUNT = "CASE WHEN si.status = 'CANCELLED' THEN 0 ELSE it.line_total - " + RETURNED_AMOUNT + " END";
    private static final String COUNTED = " FILTER (WHERE si.status <> 'CANCELLED')";

    private final NamedParameterJdbcTemplate jdbc;

    public SampleReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static Where where(SampleReportFilter f) {
        return new Where()
                .add(f.representativeId() != null, "si.representative_id = :repId", "repId", f.representativeId())
                .add(f.customerId() != null, "si.customer_id = :customerId", "customerId", f.customerId())
                .add(f.productId() != null, "it.product_id = :productId", "productId", f.productId())
                .add(f.from() != null, "si.issue_date >= :from", "from", f.from())
                .add(f.to() != null, "si.issue_date <= :to", "to", f.to())
                .add(f.status() != null, "si.status = :status", "status", f.status() == null ? null : f.status().name());
    }

    // ---------------------------------------------------------------- detail (one row per issued book)

    private static final RowMapper<SampleLineRow> LINE = (rs, n) -> new SampleLineRow(
            rs.getLong("sample_id"), rs.getLong("item_id"), rs.getString("sample_number"),
            rs.getObject("issue_date", LocalDate.class), rs.getLong("rep_id"), rs.getString("rep_name"),
            (Long) rs.getObject("customer_id"), rs.getString("customer_name"),
            rs.getLong("product_id"), rs.getString("title"), rs.getString("isbn"), rs.getInt("quantity"),
            rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total"), rs.getInt("returned_qty"),
            rs.getBigDecimal("returned_amount"), rs.getInt("outstanding_qty"), rs.getBigDecimal("outstanding_amount"),
            SampleStatus.valueOf(rs.getString("status")));

    public List<SampleLineRow> lineRows(Where w, Paging page) {
        return jdbc.query("SELECT si.id AS sample_id, it.id AS item_id, si.sample_number, si.issue_date, r.id AS rep_id, "
                        + "r.name AS rep_name, si.customer_id, si.customer_name, p.id AS product_id, p.title, p.isbn, it.quantity, it.unit_price, it.line_total, "
                        + RETURNED_QTY + " AS returned_qty, " + RETURNED_AMOUNT + " AS returned_amount, "
                        + OUT_QTY + " AS outstanding_qty, " + OUT_AMOUNT + " AS outstanding_amount, si.status"
                        + FROM + w.sql() + " ORDER BY " + page.orderBy() + " LIMIT :limit OFFSET :offset",
                paged(w, page), LINE);
    }

    public SampleReportSummary lineSummary(Where w) {
        return jdbc.queryForObject("SELECT count(*), count(DISTINCT si.id)" + COUNTED + ", "
                        + "coalesce(sum(it.quantity)" + COUNTED + ", 0), coalesce(sum(it.line_total)" + COUNTED + ", 0), "
                        + "coalesce(sum(" + RETURNED_QTY + ")" + COUNTED + ", 0), coalesce(sum(" + RETURNED_AMOUNT + ")" + COUNTED + ", 0), "
                        + "coalesce(sum(" + OUT_QTY + "), 0), coalesce(sum(" + OUT_AMOUNT + "), 0)"
                        + FROM + w.sql(),
                w.params(), SampleReportRepository::summary);
    }

    // ---------------------------------------------------------------- per representative

    /** Non-cancelled lines matching the filters, with what was returned of each. */
    private static String repLines(Where w) {
        String clause = w.sql().isEmpty() ? " WHERE si.status <> 'CANCELLED'" : w.sql() + " AND si.status <> 'CANCELLED'";
        return "(SELECT si.id AS sample_id, si.issue_date, r.id AS rep_id, r.name, r.area, r.active, it.quantity, "
                + "it.line_total, " + RETURNED_QTY + " AS rq, " + RETURNED_AMOUNT + " AS ra" + FROM + clause + ") x";
    }

    public List<SampleRepresentativeRow> representativeRows(Where w, Paging page) {
        return jdbc.query("""
                        SELECT rep_id AS id, name, area, active, count(DISTINCT sample_id) AS samples,
                               sum(quantity) AS issued_qty, sum(line_total) AS issued_amount,
                               sum(rq) AS returned_qty, sum(ra) AS returned_amount,
                               sum(quantity - rq) AS outstanding_qty, sum(line_total - ra) AS outstanding_amount,
                               max(issue_date) AS last_issue
                        FROM\s""" + repLines(w) + " GROUP BY rep_id, name, area, active ORDER BY " + page.orderBy()
                        + " LIMIT :limit OFFSET :offset",
                paged(w, page), (rs, n) -> new SampleRepresentativeRow(rs.getLong("id"), rs.getString("name"),
                        rs.getString("area"), rs.getBoolean("active"), rs.getLong("samples"), rs.getLong("issued_qty"),
                        rs.getBigDecimal("issued_amount"), rs.getLong("returned_qty"), rs.getBigDecimal("returned_amount"),
                        rs.getLong("outstanding_qty"), rs.getBigDecimal("outstanding_amount"),
                        rs.getObject("last_issue", LocalDate.class)));
    }

    public SampleReportSummary representativeSummary(Where w) {
        return jdbc.queryForObject("""
                        SELECT count(DISTINCT rep_id), count(DISTINCT sample_id),
                               coalesce(sum(quantity), 0), coalesce(sum(line_total), 0),
                               coalesce(sum(rq), 0), coalesce(sum(ra), 0),
                               coalesce(sum(quantity - rq), 0), coalesce(sum(line_total - ra), 0)
                        FROM\s""" + repLines(w),
                w.params(), SampleReportRepository::summary);
    }

    // ---------------------------------------------------------------- helpers

    private static SampleReportSummary summary(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new SampleReportSummary(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4), rs.getLong(5),
                rs.getBigDecimal(6), rs.getLong(7), rs.getBigDecimal(8));
    }

    private static MapSqlParameterSource paged(Where w, Paging page) {
        return new MapSqlParameterSource(w.params().getValues())
                .addValue("limit", page.limit())
                .addValue("offset", page.offset());
    }
}
