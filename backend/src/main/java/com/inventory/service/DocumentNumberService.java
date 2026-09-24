package com.inventory.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Issues PUR-2026-000001 / INV-2026-000001 style numbers. The counter is incremented with an atomic
 * upsert inside the caller's transaction: concurrent saves wait on the row lock, and a failed save
 * rolls its number back, so numbers are unique and gap-free. The sequence restarts each year
 * (the year the document is entered, in the shop's time zone).
 */
@Service
public class DocumentNumberService {

    public enum DocumentType {
        PURCHASE("PUR"),
        SALE("INV");

        private final String prefix;

        DocumentType(String prefix) {
            this.prefix = prefix;
        }
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zone;

    public DocumentNumberService(JdbcTemplate jdbc, Clock clock, ZoneId businessZone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        int year = LocalDate.now(clock.withZone(zone)).getYear();
        Integer value = jdbc.queryForObject("""
                insert into document_sequence (doc_type, year, last_value) values (?, ?, 1)
                on conflict (doc_type, year) do update set last_value = document_sequence.last_value + 1
                returning last_value""", Integer.class, type.name(), year);
        return "%s-%d-%06d".formatted(type.prefix, year, value);
    }
}
