package com.inventory.controller;

import com.inventory.dto.role.RoleDtos.PermissionResponse;
import com.inventory.dto.role.RoleDtos.RoleRequest;
import com.inventory.dto.role.RoleDtos.RoleResponse;
import com.inventory.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** Also needed by user management to populate the role picker. */
    @GetMapping("/api/roles")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE', 'USER_MANAGE')")
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @GetMapping("/api/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleResponse get(@PathVariable Long id) {
        return roleService.get(id);
    }

    @PostMapping("/api/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(request));
    }

    @PutMapping("/api/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/api/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public List<PermissionResponse> permissions() {
        return roleService.permissions();
    }
}
