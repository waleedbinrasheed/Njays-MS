package com.inventory.entity;

/** Where the cloth for a line comes from. STOCK lines use the shop's fabric and move stock; CUSTOMER lines do not. */
public enum FabricSource {
    STOCK,
    CUSTOMER
}
