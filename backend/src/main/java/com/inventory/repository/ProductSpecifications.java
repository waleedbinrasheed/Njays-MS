package com.inventory.repository;

import com.inventory.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Composable filters for product lists and product search. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** Matches ISBN (hyphens/spaces ignored), title or author. */
    public static Specification<Product> matches(String search) {
        if (search == null || search.isBlank()) return null;
        String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        String isbnTerm = search.replaceAll("[\s-]", "").toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), term),
                cb.like(cb.lower(cb.coalesce(root.get("author"), "")), term),
                isbnTerm.isEmpty() ? cb.disjunction() : cb.like(root.get("isbn"), "%" + isbnTerm + "%"));
    }

    public static Specification<Product> active(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<Product> publisher(Long publisherId) {
        return publisherId == null ? null : (root, query, cb) -> cb.equal(root.get("publisher").get("id"), publisherId);
    }

    public static Specification<Product> category(Long categoryId) {
        return categoryId == null ? null : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }
}
