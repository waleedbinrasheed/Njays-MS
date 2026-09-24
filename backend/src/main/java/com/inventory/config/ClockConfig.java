package com.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Injectable clock so time-dependent logic can be tested. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Business time zone (timestamps are stored in UTC; days and months are counted here). */
    @Bean
    public ZoneId businessZone(AppProperties properties) {
        return ZoneId.of(properties.timezone());
    }
}
