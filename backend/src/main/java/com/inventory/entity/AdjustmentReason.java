package com.inventory.entity;

public enum AdjustmentReason {
    /** Fabric received into the shop. Must increase stock. */
    STOCK_IN,
    DAMAGED,
    LOST,
    COUNT_CORRECTION,
    FOUND,
    OTHER
}
