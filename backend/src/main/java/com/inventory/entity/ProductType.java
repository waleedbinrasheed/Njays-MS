package com.inventory.entity;

/**
 * BOOK rows are legacy data kept for history only. FABRIC rows are the fabrics the shop sells per metre.
 * Widen together with the chk_product_type constraint when another product type is introduced.
 */
public enum ProductType {
    BOOK,
    FABRIC
}
