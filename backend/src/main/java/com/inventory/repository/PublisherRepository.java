package com.inventory.repository;

import com.inventory.entity.Publisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    @Query("""
            select x from Publisher x
            where (:active is null or x.active = :active)
              and (:search = ''
                   or lower(x.name) like concat('%', :search, '%')
                   or lower(coalesce(x.contactPerson, '')) like concat('%', :search, '%')
                   or lower(coalesce(x.phone, '')) like concat('%', :search, '%')
                   or lower(coalesce(x.email, '')) like concat('%', :search, '%'))
            """)
    Page<Publisher> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
