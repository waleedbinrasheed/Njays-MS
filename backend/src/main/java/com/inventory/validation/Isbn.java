package com.inventory.validation;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ISBNs are stored without separators so "978-969-35-0000-1" and "9789693500001" are the same book.
 * Only the format is checked (not the check digit) because locally printed books sometimes carry
 * ISBNs with an incorrect check digit, and the shop still needs to sell them.
 */
public final class Isbn {

    private static final Pattern WELL_FORMED = Pattern.compile("^([0-9]{9}[0-9X]|[0-9]{13})$");

    private Isbn() {
    }

    /** Strips spaces and hyphens and upper-cases a trailing x; returns null for blank input. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isWellFormed(String normalized) {
        return WELL_FORMED.matcher(normalized).matches();
    }
}
