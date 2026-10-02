package com.inventory.entity;

/** Worked out from the returns recorded against a sample issue; never set by hand. */
public enum SampleStatus {
    /** Nothing returned yet. */
    ISSUED,
    PARTIALLY_RETURNED,
    /** Every issued book has come back. */
    RETURNED,
    /** Cancelled before anything was returned; all stock went back. */
    CANCELLED
}
