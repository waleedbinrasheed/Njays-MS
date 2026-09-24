package com.inventory.repository;

import com.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long>, JpaSpecificationExecutor<Inventory> {

    /**
     * SELECT ... FOR UPDATE on the stock row. Concurrent movements of the same product queue here,
     * so each one sees the quantity left by the previous one. Times out instead of waiting forever.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("select i from Inventory i join fetch i.product where i.product.id = :productId")
    Optional<Inventory> lockByProductId(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"product", "product.publisher", "product.category"})
    @Query("select i from Inventory i where i.product.id = :productId")
    Optional<Inventory> findDetailedByProductId(@Param("productId") Long productId);

    @Override
    @EntityGraph(attributePaths = {"product", "product.publisher", "product.category"})
    Page<Inventory> findAll(Specification<Inventory> spec, Pageable pageable);

    /** [products, total units, low-stock count, out-of-stock count] over active products. */
    @Query("""
            select count(i),
                   coalesce(sum(i.quantity), 0),
                   coalesce(sum(case when i.quantity <= i.reorderLevel then 1 else 0 end), 0),
                   coalesce(sum(case when i.quantity = 0 then 1 else 0 end), 0)
            from Inventory i
            where i.product.active = true
            """)
    Object[] summaryRow();
}
