package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.document.DocumentDtos.CancelRequest;
import com.inventory.dto.report.ReportDtos.ReportPage;
import com.inventory.dto.sample.SampleDtos.CreateSampleRequest;
import com.inventory.dto.sample.SampleDtos.SampleDetail;
import com.inventory.dto.sample.SampleDtos.SampleLineRow;
import com.inventory.dto.sample.SampleDtos.SampleReportSummary;
import com.inventory.dto.sample.SampleDtos.SampleRepresentativeRow;
import com.inventory.dto.sample.SampleDtos.SampleSummary;
import com.inventory.entity.SampleStatus;
import com.inventory.repository.SampleReportRepository.SampleReportFilter;
import com.inventory.service.SampleReportService;
import com.inventory.service.SampleService;
import com.inventory.service.SampleService.SampleFilter;
import com.inventory.validation.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** Sample issues ("SAMPLE SALE") and the Sample report. Kept apart from sales on purpose. */
@RestController
public class SampleController {

    private static final SortWhitelist SORTABLE = SortWhitelist.aliased(Map.of(
            "issueDate", "issueDate",
            "sampleNumber", "sampleNumber",
            "total", "total",
            "representative", "representative.name",
            "status", "status",
            "createdAt", "createdAt",
            "id", "id"));

    private final SampleService service;
    private final SampleReportService reports;

    public SampleController(SampleService service, SampleReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    @GetMapping("/api/samples")
    @PreAuthorize("hasAuthority('SAMPLE_VIEW')")
    public PageResponse<SampleSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long representativeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SampleStatus status,
            @SortDefault.SortDefaults({@SortDefault(sort = "issueDate", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "id", direction = Sort.Direction.DESC)}) @PageableDefault Pageable pageable) {
        return service.search(new SampleFilter(search, representativeId, productId, from, to, status), SORTABLE.check(pageable));
    }

    @GetMapping("/api/samples/{id}")
    @PreAuthorize("hasAuthority('SAMPLE_VIEW')")
    public SampleDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/api/samples")
    @PreAuthorize("hasAuthority('SAMPLE_MANAGE')")
    public ResponseEntity<SampleDetail> create(@Valid @RequestBody CreateSampleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/api/samples/{id}/cancel")
    @PreAuthorize("hasAuthority('SAMPLE_MANAGE')")
    public SampleDetail cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request);
    }

    /** One row per issued book (also each representative's sample history). */
    @GetMapping("/api/sample-reports/lines")
    @PreAuthorize("hasAuthority('SAMPLE_VIEW')")
    public ReportPage<SampleLineRow, SampleReportSummary> reportLines(
            @RequestParam(required = false) Long representativeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SampleStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        return reports.lines(new SampleReportFilter(representativeId, productId, from, to, status), pageable);
    }

    @GetMapping("/api/sample-reports/representatives")
    @PreAuthorize("hasAuthority('SAMPLE_VIEW')")
    public ReportPage<SampleRepresentativeRow, SampleReportSummary> reportRepresentatives(
            @RequestParam(required = false) Long representativeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SampleStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        return reports.representatives(new SampleReportFilter(representativeId, productId, from, to, status), pageable);
    }
}
