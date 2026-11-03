package com.inventory.repository;

import com.inventory.entity.GarmentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GarmentTypeRepository extends JpaRepository<GarmentType, Long> {

    @Query(value = """
            select g from GarmentType g
            where (:active is null or g.active = :active)
              and (:search = '' or lower(g.name) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(g) from GarmentType g
            where (:active is null or g.active = :active)
              and (:search = '' or lower(g.name) like concat('%', :search, '%'))
            """)
    Page<GarmentType> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
