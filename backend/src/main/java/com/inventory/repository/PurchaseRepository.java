package com.inventory.repository;

import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.Purchase;
import com.inventory.entity.PurchaseItem;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {

    @Override
    @EntityGraph(attributePaths = "supplier")
    Page<Purchase> findAll(Specification<Purchase> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "items", "items.product", "cancelledBy"})
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findDetailedById(@Param("id") Long id);

    /** Serialises cancel / payment updates of the same purchase. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> lockById(@Param("id") Long id);

    boolean existsBySupplierId(Long supplierId);

    final class Filters {

        private Filters() {
        }

        /** Purchase number, supplier name or the supplier's own invoice number. */
        public static Specification<Purchase> matches(String search) {
            if (search == null || search.isBlank()) return null;
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            return (root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("purchaseNumber")), term),
                    cb.like(cb.lower(root.get("supplier").get("name")), term),
                    cb.like(cb.lower(cb.coalesce(root.get("supplierInvoiceNumber"), "")), term));
        }

        public static Specification<Purchase> supplier(Long supplierId) {
            return supplierId == null ? null : (root, q, cb) -> cb.equal(root.get("supplier").get("id"), supplierId);
        }

        /** Purchases containing the product (any line). */
        public static Specification<Purchase> product(Long productId) {
            if (productId == null) return null;
            return (root, q, cb) -> {
                Subquery<Long> sub = q.subquery(Long.class);
                var item = sub.from(PurchaseItem.class);
                sub.select(item.get("id")).where(
                        cb.equal(item.get("purchase"), root),
                        cb.equal(item.get("product").get("id"), productId));
                return cb.exists(sub);
            };
        }

        public static Specification<Purchase> dateFrom(LocalDate from) {
            return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("purchaseDate"), from);
        }

        public static Specification<Purchase> dateTo(LocalDate to) {
            return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("purchaseDate"), to);
        }

        public static Specification<Purchase> paymentStatus(PaymentStatus status) {
            return status == null ? null : (root, q, cb) -> cb.equal(root.get("paymentStatus"), status);
        }

        public static Specification<Purchase> status(DocumentStatus status) {
            return status == null ? null : (root, q, cb) -> cb.equal(root.get("status"), status);
        }
    }
}
