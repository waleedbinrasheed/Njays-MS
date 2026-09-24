package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.user.UserDtos.CreateUserRequest;
import com.inventory.dto.user.UserDtos.ResetPasswordRequest;
import com.inventory.dto.user.UserDtos.UpdateUserRequest;
import com.inventory.dto.user.UserDtos.UserResponse;
import com.inventory.entity.AppUser;
import com.inventory.entity.Permission;
import com.inventory.entity.Role;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.UserMapper;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.RoleRepository;
import com.inventory.security.AppUserPrincipal;
import com.inventory.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String search, Boolean active, Pageable pageable) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return PageResponse.of(users.search(term, active, pageable), UserMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserMapper.toResponse(load(id));
    }

    /** Users created by an admin get a temporary password and must change it at first login. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        String email = blankToNull(request.email());

        if (users.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Username '" + username + "' is already taken");
        }
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email '" + email + "' is already in use");
        }

        AppUser user = new AppUser(username, passwordEncoder.encode(request.password()), request.fullName().trim());
        user.setEmail(email);
        user.setMobile(blankToNull(request.mobile()));
        user.setActive(request.active() == null || request.active());
        user.setMustChangePassword(true);
        user.replaceRoles(resolveAssignableRoles(request.roleIds()));

        AppUser saved = users.save(user);
        log.info("User '{}' created by user id {}", username, CurrentUser.id().orElse(null));
        return UserMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUser user = load(id);
        requireCanManage(user);
        String email = blankToNull(request.email());
        if (email != null && users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email '" + email + "' is already in use");
        }

        Set<Role> newRoles = resolveAssignableRoles(request.roleIds());
        boolean staysAdmin = newRoles.stream().anyMatch(Role::isAdmin);
        guardAdministratorAccess(user, request.active(), staysAdmin);

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setMobile(blankToNull(request.mobile()));
        user.setActive(request.active());
        user.replaceRoles(newRoles);
        return UserMapper.toResponse(users.saveAndFlush(user));
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        AppUser user = load(id);
        requireCanManage(user);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(true);
        log.info("Password of user '{}' reset by user id {}", user.getUsername(), CurrentUser.id().orElse(null));
    }

    /**
     * Admins cannot lock themselves out, and the system always keeps one active administrator.
     */
    private void guardAdministratorAccess(AppUser user, boolean willBeActive, boolean willBeAdmin) {
        boolean isSelf = CurrentUser.id().map(user.getId()::equals).orElse(false);
        if (isSelf && !willBeActive) {
            throw new BusinessException("You cannot deactivate your own account");
        }
        if (isSelf && user.hasRole(Role.ADMIN) && !willBeAdmin) {
            throw new BusinessException("You cannot remove the ADMIN role from your own account");
        }
        boolean isActiveAdmin = user.isActive() && user.hasRole(Role.ADMIN);
        boolean losesAdminAccess = !willBeActive || !willBeAdmin;
        if (isActiveAdmin && losesAdminAccess && users.countActiveAdmins() <= 1) {
            throw new BusinessException("At least one active administrator is required");
        }
    }

    /** Prevents privilege escalation: nobody can hand out permissions they do not hold themselves. */
    private Set<Role> resolveAssignableRoles(Set<Long> roleIds) {
        List<Role> found = roles.findAllById(roleIds);
        if (found.size() != roleIds.size()) {
            throw new BusinessException("One or more selected roles do not exist");
        }
        if (exceedsCallerPermissions(found)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You cannot assign a role with permissions you do not have");
        }
        return new HashSet<>(found);
    }

    /** A user manager cannot edit (or reset the password of) someone with more access than themselves. */
    private void requireCanManage(AppUser target) {
        if (exceedsCallerPermissions(target.getRoles())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You cannot modify a user who has permissions you do not have");
        }
    }

    private static boolean exceedsCallerPermissions(Collection<Role> roleSet) {
        Set<String> mine = CurrentUser.principal().map(AppUserPrincipal::getPermissions).orElse(Set.of());
        return roleSet.stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .anyMatch(code -> !mine.contains(code));
    }

    private AppUser load(Long id) {
        return users.findWithRolesById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
