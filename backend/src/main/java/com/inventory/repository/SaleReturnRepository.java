package com.inventory.repository;

import com.inventory.entity.SaleReturn;
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

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long>, JpaSpecificationExecutor<SaleReturn> {

    @Override
    @EntityGraph(attributePaths = {"sale", "sale.representative", "sale.customer"})
    Page<SaleReturn> findAll(Specification<SaleReturn> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"sale", "sale.representative", "sale.customer", "items", "items.product"})
    @Query("select r from SaleReturn r where r.id = :id")
    Optional<SaleReturn> findDetailedById(@Param("id") Long id);

    boolean existsBySaleId(Long saleId);

    /** Per original line: [line id, quantity already returned, amount already returned]. */
    @Query("""
            select i.saleItem.id, sum(i.quantity), sum(i.lineTotal)
            from SaleReturnItem i
            where i.saleReturn.sale.id = :saleId
            group by i.saleItem.id
            """)
    List<Object[]> returnedPerLine(@Param("saleId") Long saleId);

    final class Filters {

        private Filters() {
        }

        /** Return number, invoice number, representative or customer name (outer joins: either may be absent). */
        public static Specification<SaleReturn> matches(String search) {
            if (search == null || search.isBlank()) return null;
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            return (root, q, cb) -> {
                var sale = root.join("sale");
                var rep = sale.join("representative", jakarta.persistence.criteria.JoinType.LEFT);
                var customer = sale.join("customer", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.or(
                        cb.like(cb.lower(root.get("returnNumber")), term),
                        cb.like(cb.lower(sale.get("invoiceNumber")), term),
                        cb.like(cb.lower(cb.coalesce(rep.get("name"), "")), term),
                        cb.like(cb.lower(cb.coalesce(customer.get("name"), "")), term));
            };
        }

        public static Specification<SaleReturn> document(Long saleId) {
            return saleId == null ? null : (root, q, cb) -> cb.equal(root.get("sale").get("id"), saleId);
        }

        public static Specification<SaleReturn> dateFrom(LocalDate from) {
            return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("returnDate"), from);
        }

        public static Specification<SaleReturn> dateTo(LocalDate to) {
            return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("returnDate"), to);
        }
    }
}
