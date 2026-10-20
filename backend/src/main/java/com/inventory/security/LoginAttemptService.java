package com.inventory.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@value #MAX_FAILURES} wrong passwords for a username within
 * {@link #WINDOW}, sign-in for that username is refused until the window has passed. A successful
 * sign-in clears the count. Kept in memory: a restart clears it, which is fine for one server.
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;

    private record Failures(int count, Instant firstAt) {
    }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** How long the username stays locked, if it is locked right now. */
    public Optional<Duration> lockedFor(String username) {
        Failures f = failures.get(key(username));
        if (f == null || f.count() < MAX_FAILURES) return Optional.empty();
        Duration left = Duration.between(Instant.now(clock), f.firstAt().plus(WINDOW));
        if (left.isNegative() || left.isZero()) {
            failures.remove(key(username), f);
            return Optional.empty();
        }
        return Optional.of(left);
    }

    public void recordFailure(String username) {
        Instant now = Instant.now(clock);
        if (failures.size() > MAX_TRACKED) {
            failures.values().removeIf(f -> f.firstAt().plus(WINDOW).isBefore(now));
        }
        failures.compute(key(username), (k, f) -> f == null || f.firstAt().plus(WINDOW).isBefore(now)
                ? new Failures(1, now)
                : new Failures(f.count() + 1, f.firstAt()));
    }

    public void recordSuccess(String username) {
        failures.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
