package com.inventory.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    /** A clock the test can move forward. */
    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T08:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovingClock clock = new MovingClock();
    private final LoginAttemptService attempts = new LoginAttemptService(clock);

    @Test
    void locksAfterFiveFailuresUntilTheWindowHasPassed() {
        for (int i = 0; i < 4; i++) attempts.recordFailure("Ali");
        assertThat(attempts.lockedFor("ali")).isEmpty();

        attempts.recordFailure(" ALI ");
        assertThat(attempts.lockedFor("ali")).contains(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(10));
        assertThat(attempts.lockedFor("ali")).contains(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5));
        assertThat(attempts.lockedFor("ali")).isEmpty();
        attempts.recordFailure("ali");
        assertThat(attempts.lockedFor("ali")).isEmpty(); // a fresh window starts
    }

    @Test
    void oldFailuresDoNotCountTowardsANewLock() {
        for (int i = 0; i < 4; i++) attempts.recordFailure("sara");
        clock.advance(Duration.ofMinutes(16));
        attempts.recordFailure("sara");
        assertThat(attempts.lockedFor("sara")).isEmpty();
    }

    @Test
    void successClearsTheCount() {
        for (int i = 0; i < 4; i++) attempts.recordFailure("omar");
        attempts.recordSuccess("omar");
        attempts.recordFailure("omar");
        assertThat(attempts.lockedFor("omar")).isEmpty();
    }
}
