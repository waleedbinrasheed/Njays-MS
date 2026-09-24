package com.inventory.entity;

/** Saved documents are never edited; cancelling reverses their stock movements. */
public enum DocumentStatus {
    COMPLETED,
    CANCELLED
}
