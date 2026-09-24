package com.inventory.entity;

/** Kinds of stock movement. The sign of the quantity is decided by the business operation. */
public enum TransactionType {
    /** Goods received from a supplier (+). */
    PURCHASE,
    /** Goods sold to a customer (−). */
    SALE,
    /** Customer returned goods (+). */
    SALE_RETURN,
    /** Goods sent back to a supplier (−). */
    PURCHASE_RETURN,
    /** Manual correction (±). */
    ADJUSTMENT,
    /** Reversal of a cancelled purchase (−). */
    PURCHASE_CANCEL,
    /** Reversal of a cancelled sale (+). */
    SALE_CANCEL
}
