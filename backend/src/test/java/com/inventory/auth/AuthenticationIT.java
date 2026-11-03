package com.inventory.auth;

import com.inventory.entity.AppUser;
import com.inventory.support.ApiTestBase;
import com.inventory.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationIT extends ApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loginReturnsProfileAndPermissionsAndRecordsLastLogin() throws Exception {
        AppUser user = testUsers.normalUser();
        assertThat(user.getLastLoginAt()).isNull();

        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", user.getUsername(), "password", TestUsers.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.getUsername()))
                .andExpect(jsonPath("$.roles[0]").value("NORMAL_USER"))
                .andExpect(jsonPath("$.permissions", hasItem("ORDER_MANAGE")))
                .andExpect(jsonPath("$.permissions", not(hasItem("USER_MANAGE"))))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.sessionTimeoutSeconds").value(1800))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Object lastLogin = jdbc.queryForObject("select last_login_at from app_user where id = ?", Object.class, user.getId());
        assertThat(lastLogin).isNotNull();
    }

    @Test
    void loginWorksWithEmailAndIsCaseInsensitive() throws Exception {
        AppUser user = testUsers.normalUser();
        login(user.getEmail().toUpperCase(), TestUsers.PASSWORD);
        login(user.getUsername().toUpperCase(), TestUsers.PASSWORD);
    }

    @Test
    void sessionIdentifiesUserOnLaterRequests() throws Exception {
        AppUser user = testUsers.normalUser();
        MockHttpSession session = login(user.getUsername());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameGenericError() throws Exception {
        AppUser user = testUsers.normalUser();

        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", user.getUsername(), "password", "Wrong1234")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", "nobody_here", "password", "Wrong1234")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void repeatedWrongPasswordsLockTheUsernameForAWhile() throws Exception {
        AppUser user = testUsers.normalUser();
        for (int i = 0; i < 5; i++) {
            mvc.perform(withJson(post("/api/auth/login"), Map.of("username", user.getUsername(), "password", "Wrong1234")))
                    .andExpect(status().isUnauthorized());
        }

        // Even the right password is refused while locked, without revealing whether it was right.
        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", user.getUsername().toUpperCase(),
                        "password", TestUsers.PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many failed sign-in attempts. Try again in 15 minutes"));

        // Other users are not affected.
        login(testUsers.normalUser().getUsername());
    }

    @Test
    void successfulSignInClearsEarlierFailures() throws Exception {
        AppUser user = testUsers.normalUser();
        for (int i = 0; i < 4; i++) {
            mvc.perform(withJson(post("/api/auth/login"), Map.of("username", user.getUsername(), "password", "Wrong1234")))
                    .andExpect(status().isUnauthorized());
        }
        login(user.getUsername());
        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", user.getUsername(), "password", "Wrong1234")))
                .andExpect(status().isUnauthorized());
        login(user.getUsername());
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        AppUser user = testUsers.create(TestUsers.uniqueName("inactive"), "NORMAL_USER", false, false);

        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", user.getUsername(), "password", TestUsers.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your account is inactive. Contact an administrator"));

        // Status is not revealed without the correct password.
        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", user.getUsername(), "password", "Wrong1234")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        AppUser user = testUsers.normalUser();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", user.getUsername(), "password", TestUsers.PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginValidatesRequiredFields() throws Exception {
        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", "", "password", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("username")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login(testUsers.normalUser().getUsername());

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredOrMissingSessionIsRejectedWithJson401() throws Exception {
        MockHttpSession session = login(testUsers.normalUser().getUsername());
        session.invalidate(); // what the container does when the inactivity timeout passes

        mvc.perform(get("/api/auth/me").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void userDeactivatedWhileLoggedInLosesAccessImmediately() throws Exception {
        AppUser admin = testUsers.admin();
        AppUser victim = testUsers.normalUser();
        MockHttpSession victimSession = login(victim.getUsername());
        MockHttpSession adminSession = login(admin.getUsername());

        mvc.perform(withJson(put("/api/users/" + victim.getId()), Map.of(
                        "fullName", victim.getFullName(), "roleIds", roleIds(victim), "active", false))
                        .session(adminSession))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(victimSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your session has ended. Please sign in again"));
    }

    @Test
    void userWhoMustChangePasswordIsLimitedUntilTheyDo() throws Exception {
        AppUser user = testUsers.create(TestUsers.uniqueName("fresh"), "ADMIN", true, true);
        MockHttpSession session = login(user.getUsername());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You must change your password before continuing"));

        mvc.perform(withJson(post("/api/auth/change-password"),
                        Map.of("currentPassword", TestUsers.PASSWORD, "newPassword", "BrandNew456")).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/users").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.mustChangePassword").value(false));
        login(user.getUsername(), "BrandNew456");
    }

    @Test
    void changePasswordValidatesCurrentAndStrength() throws Exception {
        MockHttpSession session = login(testUsers.normalUser().getUsername());

        mvc.perform(withJson(post("/api/auth/change-password"),
                        Map.of("currentPassword", "Wrong1234", "newPassword", "BrandNew456")).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));

        mvc.perform(withJson(post("/api/auth/change-password"),
                        Map.of("currentPassword", TestUsers.PASSWORD, "newPassword", "short")).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

        mvc.perform(withJson(post("/api/auth/change-password"),
                        Map.of("currentPassword", TestUsers.PASSWORD, "newPassword", TestUsers.PASSWORD)).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be different from the current password"));
    }

    @Test
    void passwordHashIsBcryptNeverPlainText() {
        AppUser user = testUsers.normalUser();
        assertThat(user.getPasswordHash()).startsWith("$2a$12$").doesNotContain(TestUsers.PASSWORD);
    }

    private Object roleIds(AppUser user) {
        return user.getRoles().stream().map(r -> r.getId()).toList();
    }
}
