package com.inventory.repository;

import com.inventory.entity.PurchaseReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long>, JpaSpecificationExecutor<PurchaseReturn> {

    @Override
    @EntityGraph(attributePaths = {"purchase", "purchase.supplier"})
    Page<PurchaseReturn> findAll(Specification<PurchaseReturn> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"purchase", "purchase.supplier", "items", "items.product"})
    @Query("select r from PurchaseReturn r where r.id = :id")
    Optional<PurchaseReturn> findDetailedById(@Param("id") Long id);

    boolean existsByPurchaseId(Long purchaseId);

    /** Per original line: [line id, quantity already returned, amount already returned]. */
    @Query("""
            select i.purchaseItem.id, sum(i.quantity), sum(i.lineTotal)
            from PurchaseReturnItem i
            where i.purchaseReturn.purchase.id = :purchaseId
            group by i.purchaseItem.id
            """)
    List<Object[]> returnedPerLine(@Param("purchaseId") Long purchaseId);

    final class Filters {

        private Filters() {
        }

        /** Return number, original document number or supplier name. */
        public static Specification<PurchaseReturn> matches(String search) {
            if (search == null || search.isBlank()) return null;
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            return (root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("returnNumber")), term),
                    cb.like(cb.lower(root.get("purchase").get("purchaseNumber")), term),
                    cb.like(cb.lower(root.get("purchase").get("supplier").get("name")), term));
        }

        public static Specification<PurchaseReturn> document(Long purchaseId) {
            return purchaseId == null ? null : (root, q, cb) -> cb.equal(root.get("purchase").get("id"), purchaseId);
        }

        public static Specification<PurchaseReturn> dateFrom(LocalDate from) {
            return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("returnDate"), from);
        }

        public static Specification<PurchaseReturn> dateTo(LocalDate to) {
            return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("returnDate"), to);
        }
    }
}
