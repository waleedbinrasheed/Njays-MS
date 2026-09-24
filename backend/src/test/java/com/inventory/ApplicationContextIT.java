package com.inventory;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application against the local PostgreSQL test database
 * (see src/test/resources/application-test.yml) and verifies Flyway ran.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextIT {

    @Autowired
    private Flyway flyway;

    @Test
    void contextLoadsAndMigrationsAreValid() {
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
    }
}
