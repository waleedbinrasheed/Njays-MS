package com.inventory.repository;

import com.inventory.entity.Inventory;
import com.inventory.entity.Product;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class InventorySpecifications {

    private InventorySpecifications() {
    }

    public enum StockStatus {
        /** quantity <= reorder level (includes out of stock), as in the Low Stock report. */
        LOW,
        OUT,
        IN_STOCK
    }

    public static Specification<Inventory> productMatches(String search) {
        if (search == null || search.isBlank()) return null;
        String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        String isbnTerm = search.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return (root, query, cb) -> {
            Path<Product> p = root.get("product");
            return cb.or(
                    cb.like(cb.lower(p.get("title")), term),
                    cb.like(cb.lower(cb.coalesce(p.get("author"), "")), term),
                    isbnTerm.isEmpty() ? cb.disjunction() : cb.like(p.get("isbn"), "%" + isbnTerm + "%"));
        };
    }

    public static Specification<Inventory> productActive(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("product").get("active"), active);
    }

    public static Specification<Inventory> category(Long categoryId) {
        return categoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("product").get("category").get("id"), categoryId);
    }

    public static Specification<Inventory> publisher(Long publisherId) {
        return publisherId == null ? null
                : (root, query, cb) -> cb.equal(root.get("product").get("publisher").get("id"), publisherId);
    }

    public static Specification<Inventory> product(Long productId) {
        return productId == null ? null : (root, query, cb) -> cb.equal(root.get("product").get("id"), productId);
    }

    public static Specification<Inventory> status(StockStatus status) {
        if (status == null) return null;
        return switch (status) {
            case LOW -> (root, query, cb) -> cb.le(root.get("quantity"), root.get("reorderLevel"));
            case OUT -> (root, query, cb) -> cb.equal(root.get("quantity"), 0);
            case IN_STOCK -> (root, query, cb) -> cb.gt(root.get("quantity"), 0);
        };
    }
}
