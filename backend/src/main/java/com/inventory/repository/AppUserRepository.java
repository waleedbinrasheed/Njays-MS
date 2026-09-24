package com.inventory.repository;

import com.inventory.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Login lookup: accepts either the username or the email address. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from AppUser u where lower(u.username) = lower(:login) or lower(u.email) = lower(:login)")
    Optional<AppUser> findForLogin(@Param("login") String login);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findWithAuthoritiesById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findWithRolesById(@Param("id") Long id);

    // Roles are loaded afterwards in batches (hibernate.default_batch_fetch_size), which keeps paging in SQL.
    @Query(value = """
            select u from AppUser u
            where (:active is null or u.active = :active)
              and (:search = ''
                   or lower(u.username) like concat('%', :search, '%')
                   or lower(u.fullName) like concat('%', :search, '%')
                   or lower(coalesce(u.email, '')) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(u) from AppUser u
            where (:active is null or u.active = :active)
              and (:search = ''
                   or lower(u.username) like concat('%', :search, '%')
                   or lower(u.fullName) like concat('%', :search, '%')
                   or lower(coalesce(u.email, '')) like concat('%', :search, '%'))
            """)
    Page<AppUser> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("select count(u) from AppUser u join u.roles r where r.id = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

    @Query("select count(u) from AppUser u join u.roles r where r.name = 'ADMIN' and u.active = true")
    long countActiveAdmins();

    /** Direct update so recording a login does not bump the optimistic-lock version. */
    @Transactional
    @Modifying
    @Query("update AppUser u set u.lastLoginAt = :at where u.id = :id")
    void recordLogin(@Param("id") Long id, @Param("at") Instant at);
}
