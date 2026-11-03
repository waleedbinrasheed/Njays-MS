export type AdjustmentReason = "STOCK_IN" | "DAMAGED" | "LOST" | "COUNT_CORRECTION" | "FOUND" | "OTHER";

export type TransactionType = "STOCK_IN" | "ORDER_ISSUE" | "ORDER_RETURN" | "ADJUSTMENT";

export interface FabricRef {
  id: number;
  code: string;
  name: string;
}

export interface InventoryRow {
  productId: number;
  code: string;
  name: string;
  material: string | null;
  colour: string | null;
  quantity: number;
  reorderLevel: number;
  stockLevel: "OUT_OF_STOCK" | "LOW" | "OK";
  active: boolean;
  updatedAt: string;
}

export interface StockTransaction {
  id: number;
  createdAt: string;
  product: FabricRef;
  type: TransactionType;
  change: number;
  resulting: number;
  user: { id: number; name: string };
  notes: string | null;
}

export interface StockAdjustmentInput {
  productId: number;
  adjustmentQuantity: number;
  reason: AdjustmentReason;
  expectedCurrentQuantity: number;
  notes: string;
}
