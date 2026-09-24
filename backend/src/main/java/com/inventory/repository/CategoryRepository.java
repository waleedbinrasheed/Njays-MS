package com.inventory.repository;

import com.inventory.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("""
            select x from Category x
            where (:active is null or x.active = :active)
              and (:search = ''
                   or lower(x.name) like concat('%', :search, '%')
                   or lower(coalesce(x.description, '')) like concat('%', :search, '%'))
            """)
    Page<Category> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
