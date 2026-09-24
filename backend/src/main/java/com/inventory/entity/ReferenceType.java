package com.inventory.entity;

/** The business document a stock transaction came from. */
public enum ReferenceType {
    PURCHASE,
    SALE,
    SALE_RETURN,
    PURCHASE_RETURN,
    ADJUSTMENT
}
