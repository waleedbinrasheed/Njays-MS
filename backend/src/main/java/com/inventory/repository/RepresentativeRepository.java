package com.inventory.repository;

import com.inventory.entity.Representative;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RepresentativeRepository extends JpaRepository<Representative, Long> {

    @Query("""
            select x from Representative x
            where (:active is null or x.active = :active)
              and (:area = '' or lower(coalesce(x.area, '')) like concat('%', :area, '%'))
              and (:search = ''
                   or lower(x.name) like concat('%', :search, '%')
                   or lower(coalesce(x.area, '')) like concat('%', :search, '%')
                   or lower(coalesce(x.phone, '')) like concat('%', :search, '%')
                   or lower(coalesce(x.email, '')) like concat('%', :search, '%'))
            """)
    Page<Representative> search(@Param("search") String search, @Param("area") String area,
                                @Param("active") Boolean active, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<Representative> findByBuiltInTrue();
}
