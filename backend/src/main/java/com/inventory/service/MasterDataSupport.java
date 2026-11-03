package com.inventory.service;

import com.inventory.entity.AuditableEntity;
import com.inventory.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Small helpers shared by the master data, inventory and order services. */
final class MasterDataSupport {

    private MasterDataSupport() {
    }

    /** Rejects an update made against an out-of-date copy of the record. */
    static void checkVersion(AuditableEntity entity, Long requestVersion) {
        if (requestVersion == null) {
            throw new BusinessException("Version is required when updating a record");
        }
        if (entity.getVersion() != requestVersion) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "This record was changed by someone else. Reload it and try again");
        }
    }

    /**
     * Records that are referenced elsewhere (a fabric with orders, a customer with orders...)
     * cannot be deleted; the database foreign keys are the final word on that.
     */
    static <E> void deleteIfUnused(JpaRepository<E, Long> repository, E entity, String label) {
        try {
            repository.delete(entity);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(label);
        }
    }

    static BusinessException inUse(String label) {
        return new BusinessException(HttpStatus.CONFLICT,
                label + " is in use and cannot be deleted. Deactivate it instead");
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String searchTerm(String search) {
        return search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }

    /** Rejects amounts with more than 2 decimal places instead of silently rounding them. */
    static BigDecimal money(BigDecimal value) {
        return exactScale(value, 2, "Amounts");
    }

    /** Fabric quantities in metres: at most 3 decimal places. */
    static BigDecimal metres(BigDecimal value) {
        return exactScale(value, 3, "Metres");
    }

    /** Rounds a computed amount to 2 decimals, half up (line amounts are computed server-side). */
    static BigDecimal roundMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Plain text for a quantity or amount without trailing zeros, for messages. */
    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal exactScale(BigDecimal value, int scale, String what) {
        if (value == null) {
            return null;
        }
        if (value.stripTrailingZeros().scale() > scale) {
            throw new BusinessException(what + " may have at most " + scale + " decimal places");
        }
        return value.setScale(scale, RoundingMode.UNNECESSARY);
    }
}
