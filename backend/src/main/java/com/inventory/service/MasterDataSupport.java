package com.inventory.service;

import com.inventory.entity.AuditableEntity;
import com.inventory.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/** Small helpers shared by the master data services. */
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
     * Records that are referenced elsewhere (a publisher with products, a product with sales...)
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
}
