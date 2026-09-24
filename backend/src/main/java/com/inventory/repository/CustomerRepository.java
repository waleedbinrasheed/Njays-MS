package com.inventory.repository;

import com.inventory.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("""
            select x from Customer x
            where (:active is null or x.active = :active)
              and (:search = ''
                   or lower(x.name) like concat('%', :search, '%')
                   or lower(coalesce(x.phone, '')) like concat('%', :search, '%')
                   or lower(coalesce(x.email, '')) like concat('%', :search, '%'))
            """)
    Page<Customer> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    Optional<Customer> findByWalkInTrue();
}
