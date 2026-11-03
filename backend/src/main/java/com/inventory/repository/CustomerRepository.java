package com.inventory.repository;

import com.inventory.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Walk-in rows (legacy) are never returned. */
    @Query(value = """
            select c from Customer c
            where c.walkIn = false
              and (:active is null or c.active = :active)
              and (:search = ''
                   or lower(c.name) like concat('%', :search, '%')
                   or lower(coalesce(c.phone, '')) like concat('%', :search, '%')
                   or lower(coalesce(c.email, '')) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(c) from Customer c
            where c.walkIn = false
              and (:active is null or c.active = :active)
              and (:search = ''
                   or lower(c.name) like concat('%', :search, '%')
                   or lower(coalesce(c.phone, '')) like concat('%', :search, '%')
                   or lower(coalesce(c.email, '')) like concat('%', :search, '%'))
            """)
    Page<Customer> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    /** Active, non-walk-in customer by id (used for order pickers). */
    Optional<Customer> findByIdAndWalkInFalse(Long id);

    /**
     * Other active customers with the same name (case-insensitive) and phone. The phone is compared as
     * an empty string when absent; the name must already be trimmed and lower-cased.
     */
    @Query("""
            select count(c) from Customer c
            where c.walkIn = false and c.active = true
              and lower(c.name) = :name and coalesce(c.phone, '') = :phone
              and (:excludeId is null or c.id <> :excludeId)
            """)
    long countActiveDuplicates(@Param("name") String name, @Param("phone") String phone,
                               @Param("excludeId") Long excludeId);
}
