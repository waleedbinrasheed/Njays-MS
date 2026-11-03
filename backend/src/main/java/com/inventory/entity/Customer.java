package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A customer who orders garments. Walk-in rows are legacy data from the book shop: they are never
 * shown in search or pickers and cannot take orders.
 */
@Entity
@Table(name = "customer")
public class Customer extends ContactPartyEntity {

    @Column(name = "walk_in", nullable = false, updatable = false)
    private boolean walkIn;

    public boolean isWalkIn() {
        return walkIn;
    }
}
