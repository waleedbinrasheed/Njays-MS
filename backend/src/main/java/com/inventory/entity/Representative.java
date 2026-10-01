package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The shop's buyer on sales: a representative takes stock for their own customers in an area.
 * Not a user account. The built-in "Counter Sales" representative covers sales over the counter.
 */
@Entity
@Table(name = "representative")
public class Representative extends ContactPartyEntity {

    @Column(length = 150)
    private String area;

    @Column(name = "built_in", nullable = false, updatable = false)
    private boolean builtIn;

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }
}
