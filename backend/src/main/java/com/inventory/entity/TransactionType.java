package com.inventory.entity;

/** Kinds of fabric stock movement. The sign of the quantity is decided by the business operation. */
public enum TransactionType {
    /** Manual correction (+ or -): stock-in, damage, loss, count corrections. */
    ADJUSTMENT,
    /** Fabric cut for an order (-). */
    ORDER_ISSUE,
    /** Fabric returned to stock when an order is cancelled (+). */
    ORDER_CANCEL
}
