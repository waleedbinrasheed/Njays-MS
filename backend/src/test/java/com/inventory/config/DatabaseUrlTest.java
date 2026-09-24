package com.inventory.config;

import com.inventory.config.DatabaseUrlEnvironmentPostProcessor.DatabaseUrl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static com.inventory.config.DatabaseUrlEnvironmentPostProcessor.normalize;
import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlTest {

    @Test
    void neonConnectionStringIsConverted() {
        DatabaseUrl db = normalize(
                "postgresql://neondb_owner:AbC%2F123@ep-cool-1.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require")
                .orElseThrow();
        assertThat(db.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://ep-cool-1.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(db.username()).isEqualTo("neondb_owner");
        assertThat(db.password()).isEqualTo("AbC/123");
    }

    @Test
    void portAndPostgresSchemeAreKept() {
        assertThat(normalize("postgres://u:p@db.example.com:6543/app").orElseThrow().jdbcUrl())
                .isEqualTo("jdbc:postgresql://db.example.com:6543/app");
    }

    @Test
    void jdbcUrlWithCredentialsIsCleaned() {
        DatabaseUrl db = normalize("jdbc:postgresql://u:p@host/db?sslmode=require").orElseThrow();
        assertThat(db.jdbcUrl()).isEqualTo("jdbc:postgresql://host/db?sslmode=require");
        assertThat(db.username()).isEqualTo("u");
    }

    @Test
    void plainJdbcUrlIsLeftAlone() {
        assertThat(normalize("jdbc:postgresql://localhost:5432/inventory")).isEmpty();
        assertThat(normalize(null)).isEmpty();
    }

    @Test
    void explicitCredentialsWinOverOnesInTheUrl() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "postgresql://urluser:urlpass@host/db")
                .withProperty("DB_USERNAME", "explicit")
                .withProperty("spring.datasource.username", "explicit");
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://host/db");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("explicit");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("urlpass");
    }
}
