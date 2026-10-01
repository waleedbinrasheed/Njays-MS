package com.inventory.repository;

import com.inventory.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * {@code representativeId} limits to one representative's customers; {@code unassigned = true}
     * returns only customers without a representative.
     */
    @EntityGraph(attributePaths = "representative")
    @Query(value = """
            select x from Customer x left join x.representative r
            where (:active is null or x.active = :active)
              and (:representativeId is null or r.id = :representativeId)
              and (:unassigned = false or r is null)
              and (:search = ''
                   or lower(x.name) like concat('%', :search, '%')
                   or lower(coalesce(x.phone, '')) like concat('%', :search, '%')
                   or lower(coalesce(x.email, '')) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(x) from Customer x left join x.representative r
            where (:active is null or x.active = :active)
              and (:representativeId is null or r.id = :representativeId)
              and (:unassigned = false or r is null)
              and (:search = ''
                   or lower(x.name) like concat('%', :search, '%')
                   or lower(coalesce(x.phone, '')) like concat('%', :search, '%')
                   or lower(coalesce(x.email, '')) like concat('%', :search, '%'))
            """)
    Page<Customer> search(@Param("search") String search, @Param("active") Boolean active,
                          @Param("representativeId") Long representativeId, @Param("unassigned") boolean unassigned,
                          Pageable pageable);

    @EntityGraph(attributePaths = "representative")
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findWithRepresentativeById(@Param("id") Long id);

    Optional<Customer> findByWalkInTrue();

    long countByRepresentativeId(Long representativeId);

    /** [representative id, number of customers] for the given representatives. */
    @Query("select c.representative.id, count(c) from Customer c where c.representative.id in :ids group by c.representative.id")
    java.util.List<Object[]> countPerRepresentative(@Param("ids") java.util.Collection<Long> ids);

    /**
     * Moves every customer of one representative to another in a single statement. The version is
     * bumped so an edit made on an older copy of one of these customers is rejected, not lost.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Customer c set c.representative.id = :toId, c.updatedAt = current_timestamp, c.updatedBy = :userId, "
            + "c.version = c.version + 1 where c.representative.id = :fromId")
    int moveRepresentative(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("userId") Long userId);
}
