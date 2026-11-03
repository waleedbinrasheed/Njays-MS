package com.inventory.security;

import com.inventory.entity.Role;
import com.inventory.repository.RoleRepository;
import com.inventory.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Authorization sweep over EVERY API endpoint the application exposes, discovered at runtime, so a
 * new endpoint that forgets its permission check fails this test:
 * <ul>
 *   <li>anonymous callers get 401 everywhere except the public endpoints;</li>
 *   <li>a signed-in user whose role has no permissions gets 403 everywhere except the few endpoints
 *       every signed-in user needs (and is refused before any request body is read: 403, never 400);</li>
 *   <li>state-changing requests without a CSRF token are refused (403) even for an administrator.</li>
 * </ul>
 */
class EndpointSecurityIT extends ApiTestBase {

    /** Reachable without signing in. */
    private static final Set<String> PUBLIC = Set.of("GET /api/health", "POST /api/auth/login", "POST /api/auth/logout");

    /** Reachable by any signed-in user, whatever their permissions. */
    private static final Set<String> ANY_SIGNED_IN_USER = Set.of(
            "GET /api/auth/me", "POST /api/auth/change-password", "GET /api/settings/business");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Autowired
    private RoleRepository roles;

    private MockHttpSession noPermissions;
    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        // Rolled back with the test transaction.
        roles.saveAndFlush(new Role("NO_PERMISSIONS_TEST", "Test role without permissions"));
        noPermissions = login(testUsers.create("NO_PERMISSIONS_TEST").getUsername());
        admin = login(testUsers.admin().getUsername());
    }

    @Test
    void sweepFindsTheWholeApi() {
        // Guards the sweep itself: if discovery broke, the other tests would pass vacuously.
        assertThat(endpoints()).hasSizeGreaterThan(40)
                .contains("POST /api/orders", "POST /api/orders/{id}/cancel", "PUT /api/users/{id}",
                        "POST /api/customers/{customerId}/measurements");
    }

    @Test
    void anonymousCallersAreRejectedEverywhereExceptPublicEndpoints() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String endpoint : endpoints()) {
            if (PUBLIC.contains(endpoint)) continue;
            int status = mvc.perform(call(endpoint).with(csrf())).andReturn().getResponse().getStatus();
            if (status != 401) problems.add(endpoint + " -> " + status);
        }
        assertThat(problems).as("endpoints reachable without signing in").isEmpty();
    }

    @Test
    void userWithoutPermissionsIsForbiddenEverywhereExceptTheBasics() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String endpoint : endpoints()) {
            if (PUBLIC.contains(endpoint) || endpoint.equals("POST /api/auth/logout")) continue;
            int status = mvc.perform(call(endpoint).with(csrf()).session(noPermissions)).andReturn().getResponse().getStatus();
            boolean allowed = ANY_SIGNED_IN_USER.contains(endpoint);
            if (allowed && (status == 401 || status == 403)) problems.add(endpoint + " should be allowed but -> " + status);
            if (!allowed && status != 403) problems.add(endpoint + " -> " + status);
        }
        assertThat(problems).as("endpoints a user without permissions can reach").isEmpty();
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRefused() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String endpoint : endpoints()) {
            if (endpoint.startsWith("GET ")) continue;
            int status = mvc.perform(call(endpoint).session(admin)).andReturn().getResponse().getStatus();
            if (status != 403) problems.add(endpoint + " -> " + status);
        }
        assertThat(problems).as("state-changing endpoints accepting requests without a CSRF token").isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    /** "METHOD /path/{var}" for every /api handler. */
    private Set<String> endpoints() {
        Set<String> result = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String path : info.getPatternValues()) {
                if (!path.startsWith("/api/")) continue;
                if (methods.isEmpty()) {
                    result.add("GET " + path);
                } else {
                    methods.forEach(m -> result.add(m.name() + " " + path));
                }
            }
        }
        return result;
    }

    /** A request to the endpoint with path variables filled in and an empty JSON body. */
    private MockHttpServletRequestBuilder call(String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        String path = parts[1].replaceAll("\\{[^}]+}", "1");
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(parts[0]), path);
        if (!parts[0].equals("GET")) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return builder;
    }
}
