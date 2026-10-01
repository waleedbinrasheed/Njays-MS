package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One of a representative's accounts. */
@Entity
@Table(name = "customer")
public class Customer extends ContactPartyEntity {

    /**
     * The seeded "Walk-in Customer" used before representatives existed. Old invoices refer to it,
     * so it stays, but it is no longer offered for new sales and needs no representative.
     */
    @Column(name = "walk_in", nullable = false, updatable = false)
    private boolean walkIn;

    /** Null only for the walk-in customer and customers created before representatives existed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_id")
    private Representative representative;

    public boolean isWalkIn() {
        return walkIn;
    }

    public Representative getRepresentative() {
        return representative;
    }

    public void setRepresentative(Representative representative) {
        this.representative = representative;
    }
}
