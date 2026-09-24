package com.inventory.auth;

import com.inventory.entity.AppUser;
import com.inventory.repository.AppUserRepository;
import com.inventory.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Tomcat + real cookies: proves the inactivity timeout actually signs the user out,
 * and that activity keeps the session alive.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.session-timeout=2s")
@ActiveProfiles("test")
@Import(TestUsers.class)
class SessionTimeoutIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private AppUserRepository users;

    private AppUser user;
    private HttpClient client;
    private CookieManager cookies;

    @BeforeEach
    void setUp() {
        user = testUsers.normalUser();
        cookies = new CookieManager();
        client = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    @AfterEach
    void cleanUp() {
        users.deleteById(user.getId());
    }

    @Test
    void idleSessionExpiresAndActiveSessionSurvives() throws Exception {
        get("/api/health"); // obtains the XSRF-TOKEN cookie
        HttpResponse<String> login = send(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}")));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"sessionTimeoutSeconds\":2");
        assertThat(login.headers().allValues("Set-Cookie"))
                .anyMatch(c -> c.startsWith("IMS_SESSION=") && c.contains("HttpOnly"));

        // Activity inside the window keeps the session alive.
        for (int i = 0; i < 3; i++) {
            Thread.sleep(1000);
            assertThat(get("/api/auth/me").statusCode()).isEqualTo(200);
        }

        Thread.sleep(3000);
        HttpResponse<String> expired = get("/api/auth/me");
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.body()).contains("\"status\":401");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow();
    }
}
