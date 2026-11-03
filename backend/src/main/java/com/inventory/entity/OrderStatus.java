package com.inventory.entity;

import java.util.List;

/**
 * Production stages in order. Forward moves go one step at a time; a move back one step is allowed
 * to fix mistakes. CANCELLED is reached only through the cancel operation.
 */
public enum OrderStatus {
    RECEIVED,
    CUTTING,
    STITCHING,
    TRIAL,
    READY,
    DELIVERED,
    CANCELLED;

    /** Stages in production order (excludes CANCELLED). */
    public static final List<OrderStatus> PIPELINE = List.of(RECEIVED, CUTTING, STITCHING, TRIAL, READY, DELIVERED);

    /** Stages in which the order itself may still be edited (lines, fabric, measurements). */
    public static final List<OrderStatus> EDITABLE = List.of(RECEIVED, CUTTING, STITCHING);

    public boolean isEditable() {
        return EDITABLE.contains(this);
    }

    public boolean isOpen() {
        return this != DELIVERED && this != CANCELLED;
    }
}
