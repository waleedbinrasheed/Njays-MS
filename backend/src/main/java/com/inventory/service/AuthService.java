package com.inventory.service;

import com.inventory.config.AppProperties;
import com.inventory.dto.auth.AuthDtos.ChangePasswordRequest;
import com.inventory.dto.auth.AuthDtos.CurrentUserResponse;
import com.inventory.dto.auth.AuthDtos.LoginRequest;
import com.inventory.entity.AppUser;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.UserMapper;
import com.inventory.repository.AppUserRepository;
import com.inventory.security.AppUserPrincipal;
import com.inventory.security.CurrentUser;
import com.inventory.security.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final CsrfAuthenticationStrategy csrfStrategy;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final LoginAttemptService loginAttempts;
    private final long sessionTimeoutSeconds;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public AuthService(AuthenticationManager authenticationManager,
                       SecurityContextRepository contextRepository,
                       CsrfTokenRepository csrfTokenRepository,
                       AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       Clock clock,
                       LoginAttemptService loginAttempts,
                       AppProperties properties) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.loginAttempts = loginAttempts;
        this.sessionTimeoutSeconds = properties.security().sessionTimeout().toSeconds();
    }

    public CurrentUserResponse login(LoginRequest request, HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        String username = request.username().trim();
        // Checked before the password, so a locked username cannot keep being guessed.
        loginAttempts.lockedFor(username).ifPresent(left -> {
            log.warn("Sign-in refused for '{}': too many failed attempts", username);
            long minutes = Math.max(1, (left.toSeconds() + 59) / 60);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed sign-in attempts. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s"));
        });
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        } catch (DisabledException ex) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Your account is inactive. Contact an administrator");
        } catch (AuthenticationException ex) {
            loginAttempts.recordFailure(username);
            log.info("Failed login attempt for '{}'", username);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        loginAttempts.recordSuccess(username);
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();

        // Always start a brand-new session on login (prevents session fixation).
        HttpSession previous = httpRequest.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setMaxInactiveInterval(Math.toIntExact(sessionTimeoutSeconds));

        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(auth);
        holder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);
        csrfStrategy.onAuthentication(auth, httpRequest, httpResponse);

        users.recordLogin(principal.getId(), Instant.now(clock));
        log.info("User '{}' signed in", principal.getUsername());
        return UserMapper.toCurrentUser(principal, sessionTimeoutSeconds);
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        holder.clearContext();
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
    }

    public CurrentUserResponse currentUser() {
        AppUserPrincipal principal = CurrentUser.principal()
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        return UserMapper.toCurrentUser(principal, sessionTimeoutSeconds);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = CurrentUser.requireId();
        AppUser user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        log.info("User '{}' changed their password", user.getUsername());
    }
}
