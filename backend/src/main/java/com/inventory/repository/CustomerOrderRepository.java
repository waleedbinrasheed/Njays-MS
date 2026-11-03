package com.inventory.repository;

import com.inventory.entity.CustomerOrder;
import com.inventory.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = {"customer"})
    @Query(value = """
            select o from CustomerOrder o
            where (:status is null or o.status = :status)
              and (:customerId is null or o.customer.id = :customerId)
              and (:dueFrom is null or o.dueDate >= :dueFrom)
              and (:dueTo is null or o.dueDate <= :dueTo)
              and (:search = '' or lower(o.orderNumber) like concat('%', :search, '%')
                   or lower(o.customer.name) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(o) from CustomerOrder o
            where (:status is null or o.status = :status)
              and (:customerId is null or o.customer.id = :customerId)
              and (:dueFrom is null or o.dueDate >= :dueFrom)
              and (:dueTo is null or o.dueDate <= :dueTo)
              and (:search = '' or lower(o.orderNumber) like concat('%', :search, '%')
                   or lower(o.customer.name) like concat('%', :search, '%'))
            """)
    Page<CustomerOrder> search(@Param("search") String search, @Param("status") OrderStatus status,
                               @Param("customerId") Long customerId, @Param("dueFrom") LocalDate dueFrom,
                               @Param("dueTo") LocalDate dueTo, Pageable pageable);

    /** Orders with a date (due, trial or delivery) in the range, excluding cancelled orders. */
    @EntityGraph(attributePaths = {"customer"})
    @Query("""
            select o from CustomerOrder o
            where o.status <> :cancelled
              and (o.dueDate between :from and :to
                   or o.trialDate between :from and :to
                   or o.deliveryDate between :from and :to)
            order by o.dueDate, o.id
            """)
    List<CustomerOrder> findForCalendar(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                        @Param("cancelled") OrderStatus cancelled);

    /** Orders by order date for the orders report; cancelled ones included (the caller excludes them from totals). */
    @EntityGraph(attributePaths = {"customer"})
    @Query(value = """
            select o from CustomerOrder o
            where (:from is null or o.orderDate >= :from)
              and (:to is null or o.orderDate <= :to)
              and (:status is null or o.status = :status)
              and (:customerId is null or o.customer.id = :customerId)
            order by o.orderDate, o.orderNumber
            """)
    List<CustomerOrder> findForReport(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                      @Param("status") OrderStatus status, @Param("customerId") Long customerId);

    @EntityGraph(attributePaths = {"customer"})
    Optional<CustomerOrder> findWithCustomerById(Long id);

    @EntityGraph(attributePaths = {"customer"})
    List<CustomerOrder> findTop8ByOrderByIdDesc();

    boolean existsByCustomerId(Long customerId);

    long countByCustomerId(Long customerId);

    /** [customer id, number of orders] for the given customers. */
    @Query("select o.customer.id, count(o) from CustomerOrder o where o.customer.id in :ids group by o.customer.id")
    List<Object[]> countPerCustomer(@Param("ids") Collection<Long> ids);

    /** SELECT ... FOR UPDATE: serialises payments and cancellations of one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> lockById(@Param("id") Long id);

    long countByStatus(OrderStatus status);

    long countByOrderDate(LocalDate day);

    /** Open orders (not delivered, not cancelled) due on the day. */
    @Query("select count(o) from CustomerOrder o where o.dueDate = :day and o.status not in :closed")
    long countDueOn(@Param("day") LocalDate day, @Param("closed") Collection<OrderStatus> closed);

    @Query("select count(o) from CustomerOrder o where o.dueDate < :day and o.status not in :closed")
    long countOverdue(@Param("day") LocalDate day, @Param("closed") Collection<OrderStatus> closed);

    /** Sum of totals of all orders that are not cancelled. */
    @Query("select coalesce(sum(o.total), 0) from CustomerOrder o where o.status <> :cancelled")
    BigDecimal sumTotalsExcluding(@Param("cancelled") OrderStatus cancelled);
}
