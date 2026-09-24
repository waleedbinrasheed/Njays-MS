package com.inventory.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Accepts database URLs in the form hosting providers show them (Neon, Render, Heroku...):
 * {@code postgresql://user:password@host[:port]/db?sslmode=require}, and turns them into the JDBC
 * URL the driver needs. Credentials embedded in the URL are used only when DB_USERNAME /
 * DB_PASSWORD are not set. Nothing is logged, so the password never appears in the logs.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String URL = "spring.datasource.url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String url = env.getProperty(URL);
        normalize(url).ifPresent(db -> {
            Map<String, Object> props = new HashMap<>();
            props.put(URL, db.jdbcUrl());
            if (db.username() != null && !StringUtils.hasText(env.getProperty("DB_USERNAME"))) {
                props.put("spring.datasource.username", db.username());
            }
            if (db.password() != null && !StringUtils.hasText(env.getProperty("DB_PASSWORD"))) {
                props.put("spring.datasource.password", db.password());
            }
            env.getPropertySources().addFirst(new MapPropertySource("normalizedDatabaseUrl", props));
        });
    }

    public record DatabaseUrl(String jdbcUrl, String username, String password) {
    }

    /** Empty when the URL is already a plain JDBC URL (nothing to change). */
    public static Optional<DatabaseUrl> normalize(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        String raw = url.trim();
        if (raw.startsWith("jdbc:")) {
            raw = raw.substring("jdbc:".length());
            // A JDBC URL is only rewritten if it wrongly carries user:password@ (the driver ignores it).
            if (!raw.contains("@")) return Optional.empty();
        }
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) return Optional.empty();

        URI uri = URI.create(raw);
        String username = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            username = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : null;
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) jdbc.append(':').append(uri.getPort());
        jdbc.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
        if (uri.getRawQuery() != null) jdbc.append('?').append(uri.getRawQuery());
        return Optional.of(new DatabaseUrl(jdbc.toString(), username, password));
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
