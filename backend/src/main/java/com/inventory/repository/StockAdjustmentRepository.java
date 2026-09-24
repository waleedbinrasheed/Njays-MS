package com.inventory.repository;

import com.inventory.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    @EntityGraph(attributePaths = {"product", "createdBy"})
    @Query("select a from StockAdjustment a where (:productId is null or a.product.id = :productId)")
    Page<StockAdjustment> search(@Param("productId") Long productId, Pageable pageable);
}
