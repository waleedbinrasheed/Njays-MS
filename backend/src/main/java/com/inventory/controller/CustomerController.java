package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.CustomerRequest;
import com.inventory.dto.master.MasterDataDtos.CustomerResponse;
import com.inventory.service.CustomerService;
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
@RequestMapping("/api/customers")
public class CustomerController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of("name", "phone", "active", "createdAt");

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public PageResponse<CustomerResponse> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) Boolean active,
                                               @PageableDefault(sort = "name") Pageable pageable) {
        return service.search(search, active, SORTABLE.check(pageable));
    }

    /** Declared before /{id} for readability; Spring matches the literal path first anyway. */
    @GetMapping("/walk-in")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public CustomerResponse walkIn() {
        return service.walkIn();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public CustomerResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
