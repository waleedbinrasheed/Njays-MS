package com.inventory.repository;

import com.inventory.entity.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    boolean existsByFabricProductId(Long productId);

    boolean existsByGarmentTypeId(Long garmentTypeId);
}
