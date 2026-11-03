package com.inventory.repository;

import com.inventory.entity.ProductType;
import com.inventory.entity.StockTransaction;
import com.inventory.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long>,
        JpaSpecificationExecutor<StockTransaction> {

    @Override
    @EntityGraph(attributePaths = {"product", "createdBy"})
    Page<StockTransaction> findAll(Specification<StockTransaction> spec, Pageable pageable);

    boolean existsByProductId(Long productId);

    final class Filters {

        private Filters() {
        }

        /** Fabric movements only. */
        public static Specification<StockTransaction> fabricsOnly() {
            return (root, q, cb) -> cb.equal(root.get("product").get("productType"), ProductType.FABRIC);
        }

        public static Specification<StockTransaction> product(Long productId) {
            return productId == null ? null : (root, q, cb) -> cb.equal(root.get("product").get("id"), productId);
        }

        public static Specification<StockTransaction> type(TransactionType type) {
            return type == null ? null : (root, q, cb) -> cb.equal(root.get("type"), type);
        }

        public static Specification<StockTransaction> from(Instant from) {
            return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
        }

        public static Specification<StockTransaction> before(Instant before) {
            return before == null ? null : (root, q, cb) -> cb.lessThan(root.get("createdAt"), before);
        }
    }
}
