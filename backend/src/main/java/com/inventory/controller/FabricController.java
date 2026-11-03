package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.fabric.FabricDtos.FabricRequest;
import com.inventory.dto.fabric.FabricDtos.FabricResponse;
import com.inventory.service.FabricService;
import com.inventory.service.FabricService.FabricFilter;
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

import java.util.Map;

/** Fabrics sold by the metre. Stock is changed through /api/stock-adjustments, not here. */
@RestController
@RequestMapping("/api/fabrics")
public class FabricController {

    private static final SortWhitelist SORTABLE = SortWhitelist.aliased(Map.of(
            "code", "product.fabricCode",
            "name", "product.title",
            "colour", "product.colour",
            "material", "product.material",
            "active", "product.active",
            "costPerMetre", "product.purchasePrice",
            "pricePerMetre", "product.sellingPrice",
            "quantity", "quantity",
            "reorderLevel", "reorderLevel"));

    private final FabricService service;

    public FabricController(FabricService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public PageResponse<FabricResponse> list(@RequestParam(required = false) String search,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(defaultValue = "false") boolean lowStockOnly,
                                             @PageableDefault(sort = "name") Pageable pageable) {
        return service.search(new FabricFilter(search, active, lowStockOnly), SORTABLE.check(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public FabricResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<FabricResponse> create(@Valid @RequestBody FabricRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public FabricResponse update(@PathVariable Long id, @Valid @RequestBody FabricRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
