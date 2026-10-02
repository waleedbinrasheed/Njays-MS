package com.inventory.service;

import com.inventory.dto.report.ReportDtos.ReportPage;
import com.inventory.dto.sample.SampleDtos.SampleLineRow;
import com.inventory.dto.sample.SampleDtos.SampleReportSummary;
import com.inventory.dto.sample.SampleDtos.SampleRepresentativeRow;
import com.inventory.repository.ReportRepository.Paging;
import com.inventory.repository.ReportRepository.Where;
import com.inventory.repository.SampleReportRepository;
import com.inventory.repository.SampleReportRepository.SampleReportFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Sample report: issued / returned / outstanding (quantity and amount), per representative or per
 * issued book. Also feeds each representative's sample history. Totals cover every matching row.
 */
@Service
public class SampleReportService {

    private static final Map<String, String> LINE_SORT = Map.of(
            "date", "si.issue_date", "number", "si.sample_number", "representative", "r.name", "product", "p.title",
            "issuedQuantity", "it.quantity", "issuedAmount", "it.line_total", "outstandingQuantity", "outstanding_qty",
            "outstandingAmount", "outstanding_amount", "status", "si.status");
    private static final Map<String, String> REPRESENTATIVE_SORT = Map.of(
            "name", "name", "area", "area", "samples", "samples", "issuedAmount", "issued_amount",
            "returnedAmount", "returned_amount", "outstandingQuantity", "outstanding_qty",
            "outstandingAmount", "outstanding_amount", "lastIssue", "last_issue");

    private final SampleReportRepository reports;

    public SampleReportService(SampleReportRepository reports) {
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public ReportPage<SampleLineRow, SampleReportSummary> lines(SampleReportFilter f, Pageable pageable) {
        ReportService.checkRange(f.from(), f.to());
        Where w = SampleReportRepository.where(f);
        Paging paging = ReportService.paging(pageable, LINE_SORT, "si.issue_date DESC, si.id DESC", "it.id");
        SampleReportSummary summary = reports.lineSummary(w);
        return ReportService.page(reports.lineRows(w, paging), summary.rows(), pageable, summary);
    }

    @Transactional(readOnly = true)
    public ReportPage<SampleRepresentativeRow, SampleReportSummary> representatives(SampleReportFilter f, Pageable pageable) {
        ReportService.checkRange(f.from(), f.to());
        Where w = SampleReportRepository.where(f);
        Paging paging = ReportService.paging(pageable, REPRESENTATIVE_SORT, "outstanding_amount DESC, name ASC", "id");
        SampleReportSummary summary = reports.representativeSummary(w);
        return ReportService.page(reports.representativeRows(w, paging), summary.rows(), pageable, summary);
    }
}
