package com.inventory.repository;

import com.inventory.entity.OrderPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {

    List<OrderPayment> findByOrderIdOrderByIdAsc(Long orderId);

    boolean existsByOrderId(Long orderId);

    /** [order id, sum of payments] for the given orders; orders without payments are absent. */
    @Query("select p.order.id, sum(p.amount) from OrderPayment p where p.order.id in :ids group by p.order.id")
    List<Object[]> sumPerOrder(@Param("ids") Collection<Long> ids);

    /** Sum of payments on orders that are not cancelled. */
    @Query("select coalesce(sum(p.amount), 0) from OrderPayment p where p.order.status <> com.inventory.entity.OrderStatus.CANCELLED")
    BigDecimal sumPaidOnLiveOrders();
}
