package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only catalogue entry, seeded by migrations. */
@Entity
@Table(name = "permission")
public class Permission {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false, updatable = false)
    private String module;

    @Column(nullable = false, updatable = false)
    private String description;

    protected Permission() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }
}
