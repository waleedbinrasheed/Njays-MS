package com.inventory.repository;

import com.inventory.entity.Inventory;
import com.inventory.entity.Product;
import com.inventory.entity.ProductType;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

/** Filters over fabric stock rows (inventory joined to its product). */
public final class InventorySpecifications {

    private InventorySpecifications() {
    }

    public enum StockStatus {
        /** quantity <= reorder level (includes out of stock), as in the Low Stock report. */
        LOW,
        OUT,
        IN_STOCK
    }

    /** Only fabrics: legacy book rows are never shown. */
    public static Specification<Inventory> fabricsOnly() {
        return (root, query, cb) -> cb.equal(root.get("product").get("productType"), ProductType.FABRIC);
    }

    /** Matches fabric code, name, colour and material. */
    public static Specification<Inventory> search(String search) {
        if (search == null || search.isBlank()) return null;
        String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            Path<Product> p = root.get("product");
            return cb.or(
                    cb.like(cb.lower(p.get("title")), term),
                    cb.like(cb.lower(cb.coalesce(p.get("fabricCode"), "")), term),
                    cb.like(cb.lower(cb.coalesce(p.get("colour"), "")), term),
                    cb.like(cb.lower(cb.coalesce(p.get("material"), "")), term));
        };
    }

    public static Specification<Inventory> productActive(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("product").get("active"), active);
    }

    public static Specification<Inventory> product(Long productId) {
        return productId == null ? null : (root, query, cb) -> cb.equal(root.get("product").get("id"), productId);
    }

    public static Specification<Inventory> status(StockStatus status) {
        if (status == null) return null;
        return switch (status) {
            case LOW -> (root, query, cb) -> cb.le(root.get("quantity"), root.get("reorderLevel"));
            case OUT -> (root, query, cb) -> cb.equal(root.get("quantity"), BigDecimal.ZERO);
            case IN_STOCK -> (root, query, cb) -> cb.gt(root.get("quantity"), BigDecimal.ZERO);
        };
    }
}
