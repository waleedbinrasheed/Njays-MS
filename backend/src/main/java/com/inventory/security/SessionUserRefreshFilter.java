package com.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Re-reads the logged-in user from the database on every request so that:
 * <ul>
 *   <li>a deactivated or deleted user loses access immediately (session is invalidated);</li>
 *   <li>role/permission changes take effect on the next request;</li>
 *   <li>a user who must change their password can only reach the password-change endpoints.</li>
 * </ul>
 * Not a Spring bean on purpose: it must only run inside the security filter chain.
 */
public class SessionUserRefreshFilter extends OncePerRequestFilter {

    /** Endpoints still reachable while a password change is pending. */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "/api/auth/me", "/api/auth/change-password", "/api/auth/logout", "/api/health");

    private final AppUserDetailsService userDetailsService;
    private final SecurityContextRepository contextRepository;
    private final JsonAuthHandlers jsonHandlers;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public SessionUserRefreshFilter(AppUserDetailsService userDetailsService,
                                    SecurityContextRepository contextRepository,
                                    JsonAuthHandlers jsonHandlers) {
        this.userDetailsService = userDetailsService;
        this.contextRepository = contextRepository;
        this.jsonHandlers = jsonHandlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = holder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal sessionUser)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AppUserPrincipal> current = userDetailsService.loadById(sessionUser.getId());
        if (current.isEmpty() || !current.get().isEnabled()) {
            endSession(request);
            jsonHandlers.writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Your session has ended. Please sign in again");
            return;
        }

        AppUserPrincipal fresh = current.get();
        fresh.eraseCredentials();
        if (!fresh.sameAccessAs(sessionUser)) {
            SecurityContext context = holder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
            holder.setContext(context);
            contextRepository.saveContext(context, request, response);
        }

        if (fresh.isMustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(request.getRequestURI())) {
            jsonHandlers.writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                    "You must change your password before continuing");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    private void endSession(HttpServletRequest request) {
        holder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
