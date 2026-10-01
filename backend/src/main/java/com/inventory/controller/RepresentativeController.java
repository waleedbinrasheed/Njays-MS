package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.MoveCustomersRequest;
import com.inventory.dto.master.MasterDataDtos.MoveCustomersResponse;
import com.inventory.dto.master.MasterDataDtos.RepresentativeRequest;
import com.inventory.dto.master.MasterDataDtos.RepresentativeResponse;
import com.inventory.service.RepresentativeService;
import com.inventory.validation.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/representatives")
public class RepresentativeController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of("name", "area", "active", "createdAt");

    private final RepresentativeService service;

    public RepresentativeController(RepresentativeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public PageResponse<RepresentativeResponse> list(@RequestParam(required = false) String search,
                                                     @RequestParam(required = false) String area,
                                                     @RequestParam(required = false) Boolean active,
                                                     @PageableDefault(sort = "name") Pageable pageable) {
        return service.search(search, area, active, SORTABLE.check(pageable));
    }

    @GetMapping("/counter")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public RepresentativeResponse counter() {
        return service.counter();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public RepresentativeResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<RepresentativeResponse> create(@Valid @RequestBody RepresentativeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public RepresentativeResponse update(@PathVariable Long id, @Valid @RequestBody RepresentativeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/move-customers")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public MoveCustomersResponse moveCustomers(@PathVariable Long id, @Valid @RequestBody MoveCustomersRequest request) {
        return service.moveCustomers(id, request);
    }
}
