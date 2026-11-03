package com.inventory.repository;

import com.inventory.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<OrderStatusHistory> findByOrderIdOrderByIdAsc(Long orderId);
}
