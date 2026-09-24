package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.PartyRequest;
import com.inventory.dto.master.MasterDataDtos.PartyResponse;
import com.inventory.service.SupplierService;
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

import static com.inventory.controller.PublisherController.PARTY_SORTABLE;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public PageResponse<PartyResponse> list(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) Boolean active,
                                            @PageableDefault(sort = "name") Pageable pageable) {
        return service.search(search, active, PARTY_SORTABLE.check(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public PartyResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<PartyResponse> create(@Valid @RequestBody PartyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public PartyResponse update(@PathVariable Long id, @Valid @RequestBody PartyRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
