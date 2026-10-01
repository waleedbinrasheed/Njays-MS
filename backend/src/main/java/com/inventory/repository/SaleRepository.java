package com.inventory.repository;

import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.Sale;
import com.inventory.entity.SaleItem;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.JoinType;
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

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    @Override
    @EntityGraph(attributePaths = {"representative", "customer"})
    Page<Sale> findAll(Specification<Sale> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"representative", "customer", "items", "items.product", "cancelledBy"})
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findDetailedById(@Param("id") Long id);

    /** Serialises cancel / payment updates of the same invoice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(@Param("id") Long id);

    boolean existsByCustomerId(Long customerId);

    boolean existsByRepresentativeId(Long representativeId);

    final class Filters {

        private Filters() {
        }

        /**
         * Invoice number, representative name/area, or customer name/phone. Outer joins: a sale may have
         * no customer (new sales) or no representative (sales from before representatives existed).
         */
        public static Specification<Sale> matches(String search) {
            if (search == null || search.isBlank()) return null;
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            return (root, q, cb) -> {
                var rep = root.join("representative", JoinType.LEFT);
                var customer = root.join("customer", JoinType.LEFT);
                return cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), term),
                        cb.like(cb.lower(cb.coalesce(rep.get("name"), "")), term),
                        cb.like(cb.lower(cb.coalesce(rep.get("area"), "")), term),
                        cb.like(cb.lower(cb.coalesce(customer.get("name"), "")), term),
                        cb.like(cb.lower(cb.coalesce(customer.get("phone"), "")), term));
            };
        }

        public static Specification<Sale> representative(Long representativeId) {
            return representativeId == null ? null
                    : (root, q, cb) -> cb.equal(root.get("representative").get("id"), representativeId);
        }

        public static Specification<Sale> customer(Long customerId) {
            return customerId == null ? null : (root, q, cb) -> cb.equal(root.get("customer").get("id"), customerId);
        }

        public static Specification<Sale> product(Long productId) {
            if (productId == null) return null;
            return (root, q, cb) -> {
                Subquery<Long> sub = q.subquery(Long.class);
                var item = sub.from(SaleItem.class);
                sub.select(item.get("id")).where(
                        cb.equal(item.get("sale"), root),
                        cb.equal(item.get("product").get("id"), productId));
                return cb.exists(sub);
            };
        }

        public static Specification<Sale> dateFrom(LocalDate from) {
            return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("saleDate"), from);
        }

        public static Specification<Sale> dateTo(LocalDate to) {
            return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("saleDate"), to);
        }

        public static Specification<Sale> paymentStatus(PaymentStatus status) {
            return status == null ? null : (root, q, cb) -> cb.equal(root.get("paymentStatus"), status);
        }

        public static Specification<Sale> status(DocumentStatus status) {
            return status == null ? null : (root, q, cb) -> cb.equal(root.get("status"), status);
        }

        public static Specification<Sale> createdBy(Long userId) {
            return userId == null ? null : (root, q, cb) -> cb.equal(root.get("createdBy"), userId);
        }
    }
}
