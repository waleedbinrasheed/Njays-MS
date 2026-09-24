package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.returns.ReturnDtos.CreateReturnRequest;
import com.inventory.dto.returns.ReturnDtos.ReturnDetail;
import com.inventory.dto.returns.ReturnDtos.ReturnSummary;
import com.inventory.dto.returns.ReturnDtos.ReturnableLine;
import com.inventory.service.SaleReturnService;
import com.inventory.service.SaleReturnService.ReturnFilter;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sale-returns")
public class SaleReturnController {

    private static final SortWhitelist SORTABLE = SortWhitelist.aliased(Map.of(
            "returnDate", "returnDate", "returnNumber", "returnNumber", "total", "total", "id", "id"));

    private final SaleReturnService service;

    public SaleReturnController(SaleReturnService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public PageResponse<ReturnSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long saleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @SortDefault.SortDefaults({@SortDefault(sort = "returnDate", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "id", direction = Sort.Direction.DESC)}) @PageableDefault Pageable pageable) {
        return service.search(new ReturnFilter(search, saleId, from, to), SORTABLE.check(pageable));
    }

    /** Lines of a sale with what can still be returned. */
    @GetMapping("/returnable/{saleId}")
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public List<ReturnableLine> returnable(@PathVariable Long saleId) {
        return service.returnable(saleId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public ReturnDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RETURN_CREATE')")
    public ResponseEntity<ReturnDetail> create(@Valid @RequestBody CreateReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }
}
