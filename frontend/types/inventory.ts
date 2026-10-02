import type { Ref } from "@/types/master";

export type StockLevel = "OUT_OF_STOCK" | "LOW" | "OK";
export type StockStatusFilter = "LOW" | "OUT" | "IN_STOCK";

export type TransactionType =
  | "PURCHASE"
  | "SALE"
  | "SALE_RETURN"
  | "PURCHASE_RETURN"
  | "ADJUSTMENT"
  | "PURCHASE_CANCEL"
  | "SALE_CANCEL"
  | "SAMPLE_ISSUE"
  | "SAMPLE_RETURN"
  | "SAMPLE_CANCEL";

export type ReferenceType =
  | "PURCHASE"
  | "SALE"
  | "SALE_RETURN"
  | "PURCHASE_RETURN"
  | "ADJUSTMENT"
  | "SAMPLE_ISSUE"
  | "SAMPLE_RETURN";

export type AdjustmentReason = "DAMAGED" | "LOST" | "COUNT_CORRECTION" | "FOUND" | "OTHER";

export interface ProductRef {
  id: number;
  title: string;
  isbn: string | null;
}

export interface InventoryItem {
  productId: number;
  isbn: string | null;
  title: string;
  author: string | null;
  publisher: Ref | null;
  category: Ref | null;
  quantity: number;
  reorderLevel: number;
  stockLevel: StockLevel;
  purchasePrice: number;
  sellingPrice: number;
  active: boolean;
  updatedAt: string;
}

export interface InventorySummary {
  totalProducts: number;
  totalQuantity: number;
  lowStockProducts: number;
  outOfStockProducts: number;
}

export interface StockTransaction {
  id: number;
  createdAt: string;
  product: ProductRef;
  type: TransactionType;
  quantity: number;
  previousQuantity: number;
  resultingQuantity: number;
  referenceType: ReferenceType | null;
  referenceId: number | null;
  user: Ref;
  notes: string | null;
}

export interface StockAdjustmentInput {
  productId: number;
  adjustmentQuantity: number;
  expectedCurrentQuantity: number;
  reason: AdjustmentReason;
  notes: string;
}

export interface StockAdjustment {
  id: number;
  product: ProductRef;
  reason: AdjustmentReason;
  previousQuantity: number;
  adjustmentQuantity: number;
  resultingQuantity: number;
  notes: string | null;
  user: Ref;
  createdAt: string;
  stockTransactionId: number | null;
}
