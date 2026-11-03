package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.measurement.MeasurementDtos.MeasurementRequest;
import com.inventory.dto.measurement.MeasurementDtos.MeasurementResponse;
import com.inventory.service.MeasurementService;
import com.inventory.validation.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Measurement records. There is deliberately no update and no delete: a changed measurement is
 * taken again as a new record, so orders keep the figures they were made from.
 */
@RestController
public class MeasurementController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of("takenOn", "createdAt");

    private final MeasurementService service;

    public MeasurementController(MeasurementService service) {
        this.service = service;
    }

    @GetMapping("/api/customers/{customerId}/measurements")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public PageResponse<MeasurementResponse> forCustomer(@PathVariable Long customerId,
                                                         @RequestParam(required = false) Long garmentTypeId,
                                                         @PageableDefault(size = 20)
                                                         @SortDefault(sort = "takenOn", direction = Sort.Direction.DESC)
                                                         Pageable pageable) {
        return service.search(customerId, garmentTypeId, null, SORTABLE.check(pageable));
    }

    @PostMapping("/api/customers/{customerId}/measurements")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<MeasurementResponse> take(@PathVariable Long customerId,
                                                    @Valid @RequestBody MeasurementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(customerId, request));
    }

    @GetMapping("/api/measurements")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public PageResponse<MeasurementResponse> list(@RequestParam(required = false) Long customerId,
                                                  @RequestParam(required = false) Long garmentTypeId,
                                                  @RequestParam(required = false) String search,
                                                  @PageableDefault(size = 20)
                                                  @SortDefault(sort = "takenOn", direction = Sort.Direction.DESC)
                                                  Pageable pageable) {
        return service.search(customerId, garmentTypeId, search, SORTABLE.check(pageable));
    }

    @GetMapping("/api/measurements/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public MeasurementResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
