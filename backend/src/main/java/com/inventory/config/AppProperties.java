package com.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Application settings under the {@code app.*} prefix. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, Bootstrap bootstrap) {

    /**
     * @param sessionTimeout inactivity period after which the user must sign in again
     */
    public record Security(Duration sessionTimeout) {
        public Security {
            if (sessionTimeout == null) sessionTimeout = Duration.ofMinutes(30);
        }
    }

    /**
     * @param adminUsername username of the first administrator created on an empty database
     * @param adminPassword its temporary password; a random one is generated and logged if blank
     */
    public record Bootstrap(String adminUsername, String adminPassword) {
        public Bootstrap {
            if (adminUsername == null || adminUsername.isBlank()) adminUsername = "admin";
        }
    }

    public AppProperties {
        if (security == null) security = new Security(null);
        if (bootstrap == null) bootstrap = new Bootstrap(null, null);
    }
}
