package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
public class Customer extends ContactPartyEntity {

    /** The single seeded "Walk-in Customer" used for counter sales; it cannot be edited away. */
    @Column(name = "walk_in", nullable = false, updatable = false)
    private boolean walkIn;

    public boolean isWalkIn() {
        return walkIn;
    }
}
