package com.inventory.service;

import com.inventory.dto.role.RoleDtos.PermissionResponse;
import com.inventory.dto.role.RoleDtos.RoleRequest;
import com.inventory.dto.role.RoleDtos.RoleResponse;
import com.inventory.entity.Permission;
import com.inventory.entity.Role;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.RoleMapper;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.PermissionRepository;
import com.inventory.repository.RoleRepository;
import com.inventory.security.AppUserPrincipal;
import com.inventory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AppUserRepository users;

    public RoleService(RoleRepository roles, PermissionRepository permissions, AppUserRepository users) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        Map<Long, Long> counts = roles.countUsersPerRole().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return roles.findAllWithPermissions().stream()
                .map(r -> RoleMapper.toResponse(r, counts.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Long id) {
        Role role = load(id);
        return RoleMapper.toResponse(role, users.countByRoleId(id));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissions() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream().map(RoleMapper::toResponse).toList();
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        String name = normalizeName(request.name());
        if (roles.existsByName(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Role '" + name + "' already exists");
        }
        Role role = new Role(name, blankToNull(request.description()));
        role.replacePermissions(resolvePermissions(request.permissions()));
        return RoleMapper.toResponse(roles.save(role), 0);
    }

    /**
     * System roles keep their names. ADMIN always holds every permission, so only its
     * description can change.
     */
    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = load(id);
        requireCallerHolds(codesOf(role), "You cannot modify a role that has permissions you do not have");
        String name = normalizeName(request.name());

        if (role.isSystemRole() && !role.getName().equals(name)) {
            throw new BusinessException("Built-in role '" + role.getName() + "' cannot be renamed");
        }
        if (!role.getName().equals(name) && roles.existsByName(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Role '" + name + "' already exists");
        }

        role.setName(name);
        role.setDescription(blankToNull(request.description()));
        if (!role.isAdmin()) {
            role.replacePermissions(resolvePermissions(request.permissions()));
        }
        return RoleMapper.toResponse(roles.saveAndFlush(role), users.countByRoleId(id));
    }

    @Transactional
    public void delete(Long id) {
        Role role = load(id);
        requireCallerHolds(codesOf(role), "You cannot delete a role that has permissions you do not have");
        if (role.isSystemRole()) {
            throw new BusinessException("Built-in role '" + role.getName() + "' cannot be deleted");
        }
        long assigned = users.countByRoleId(id);
        if (assigned > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Role '" + role.getName() + "' is assigned to " + assigned + " user(s) and cannot be deleted");
        }
        roles.delete(role);
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        List<Permission> found = permissions.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            Set<String> known = found.stream().map(Permission::getCode).collect(Collectors.toSet());
            String unknown = codes.stream().filter(c -> !known.contains(c)).sorted().collect(Collectors.joining(", "));
            throw new BusinessException("Unknown permission(s): " + unknown);
        }
        requireCallerHolds(codes, "You cannot grant permissions you do not have");
        return new HashSet<>(found);
    }

    /** Prevents privilege escalation through role editing. */
    private static void requireCallerHolds(Set<String> codes, String message) {
        Set<String> mine = CurrentUser.principal().map(AppUserPrincipal::getPermissions).orElse(Set.of());
        if (!mine.containsAll(codes)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, message);
        }
    }

    private static Set<String> codesOf(Role role) {
        return role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
    }

    private Role load(Long id) {
        return roles.findWithPermissionsById(id).orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    /** "Store manager" → "STORE_MANAGER". */
    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
