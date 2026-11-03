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

import java.util.List;
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

    @EntityGraph(attributePaths = {"product"})
    @Query("select i from Inventory i where i.product.id = :productId")
    Optional<Inventory> findDetailedByProductId(@Param("productId") Long productId);

    @Override
    @EntityGraph(attributePaths = {"product"})
    Page<Inventory> findAll(Specification<Inventory> spec, Pageable pageable);

    /** Stock rows of every fabric, active or not. Used for the summary figures. */
    @EntityGraph(attributePaths = {"product"})
    @Query("select i from Inventory i where i.product.productType = com.inventory.entity.ProductType.FABRIC")
    List<Inventory> findAllFabricStock();
}
