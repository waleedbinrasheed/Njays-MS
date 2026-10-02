package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.sample.SampleDtos.CreateSampleReturnRequest;
import com.inventory.dto.sample.SampleDtos.SampleReturnDetail;
import com.inventory.dto.sample.SampleDtos.SampleReturnSummary;
import com.inventory.service.SampleReturnService;
import com.inventory.service.SampleReturnService.SampleReturnFilter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/sample-returns")
public class SampleReturnController {

    private static final SortWhitelist SORTABLE = SortWhitelist.aliased(Map.of(
            "returnDate", "returnDate", "returnNumber", "returnNumber", "total", "total", "id", "id"));

    private final SampleReturnService service;

    public SampleReturnController(SampleReturnService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SAMPLE_VIEW')")
    public PageResponse<SampleReturnSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long sampleId,
            @RequestParam(required = false) Long representativeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @SortDefault.SortDefaults({@SortDefault(sort = "returnDate", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "id", direction = Sort.Direction.DESC)}) @PageableDefault Pageable pageable) {
        return service.search(new SampleReturnFilter(search, sampleId, representativeId, from, to), SORTABLE.check(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SAMPLE_VIEW')")
    public SampleReturnDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SAMPLE_MANAGE')")
    public ResponseEntity<SampleReturnDetail> create(@Valid @RequestBody CreateSampleReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }
}
