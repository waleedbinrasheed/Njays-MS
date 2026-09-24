package com.inventory.config;

import com.inventory.security.AppUserDetailsService;
import com.inventory.security.JsonAuthHandlers;
import com.inventory.security.SessionUserRefreshFilter;
import com.inventory.security.SpaCsrfTokenRequestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Session-cookie security for the REST API.
 * <ul>
 *   <li>Authentication state lives in the server-side HTTP session (HttpOnly cookie).</li>
 *   <li>CSRF protection uses a readable XSRF-TOKEN cookie echoed as the X-XSRF-TOKEN header.</li>
 *   <li>Everything under /api requires authentication except the explicitly public endpoints;
 *       fine-grained rules are {@code @PreAuthorize} permission checks on controllers.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] MASTER_DATA = {
            "/api/products/**", "/api/publishers/**", "/api/categories/**", "/api/suppliers/**", "/api/customers/**"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JsonAuthHandlers authHandlers,
                                                   AppUserDetailsService userDetailsService,
                                                   SecurityContextRepository contextRepository,
                                                   CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(sc -> sc.securityContextRepository(contextRepository))
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        // Coarse URL rules for admin areas, checked before any input is read.
                        // Controllers repeat them with @PreAuthorize (defence in depth).
                        .requestMatchers("/api/users/**").hasAuthority("USER_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/roles").hasAnyAuthority("ROLE_MANAGE", "USER_MANAGE")
                        .requestMatchers("/api/roles/**", "/api/permissions/**").hasAuthority("ROLE_MANAGE")
                        .requestMatchers(HttpMethod.GET, MASTER_DATA).hasAuthority("MASTER_DATA_VIEW")
                        .requestMatchers(MASTER_DATA).hasAuthority("MASTER_DATA_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/inventory/*/reorder-level").hasAuthority("STOCK_ADJUST")
                        .requestMatchers(HttpMethod.POST, "/api/stock-adjustments").hasAuthority("STOCK_ADJUST")
                        .requestMatchers(HttpMethod.GET, "/api/inventory/**", "/api/stock-transactions/**",
                                "/api/stock-adjustments/**").hasAuthority("INVENTORY_VIEW")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionUserRefreshFilter(userDetailsService, contextRepository, authHandlers),
                        AuthorizationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authHandlers)
                        .accessDeniedHandler(authHandlers))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(c -> { }));
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    /**
     * Password is verified before the account status is checked, so an inactive account
     * is only revealed to someone who already knows its password.
     */
    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("User is inactive");
            }
        });
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
