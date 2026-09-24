package com.inventory.repository;

import com.inventory.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = "permissions")
    @Query("select r from Role r order by r.name")
    List<Role> findAllWithPermissions();

    @EntityGraph(attributePaths = "permissions")
    @Query("select r from Role r where r.id = :id")
    Optional<Role> findWithPermissionsById(@Param("id") Long id);

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    /** Role id → number of users holding it, for the roles list. */
    @Query("select r.id, count(u) from AppUser u join u.roles r group by r.id")
    List<Object[]> countUsersPerRole();
}
