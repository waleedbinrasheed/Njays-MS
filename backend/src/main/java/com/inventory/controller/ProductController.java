package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.ProductRequest;
import com.inventory.dto.master.MasterDataDtos.ProductResponse;
import com.inventory.service.ProductService;
import com.inventory.service.ProductService.ProductFilter;
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

/** Books/products. Search matches ISBN (with or without hyphens), title and author. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final SortWhitelist SORTABLE =
            SortWhitelist.of("title", "author", "isbn", "sellingPrice", "purchasePrice", "createdAt", "updatedAt");

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public PageResponse<ProductResponse> list(@RequestParam(required = false) String search,
                                              @RequestParam(required = false) Boolean active,
                                              @RequestParam(required = false) Long publisherId,
                                              @RequestParam(required = false) Long categoryId,
                                              @PageableDefault(sort = "title") Pageable pageable) {
        return productService.search(new ProductFilter(search, active, publisherId, categoryId), SORTABLE.check(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_VIEW')")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
