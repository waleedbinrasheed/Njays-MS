import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type { InventoryRow, StockAdjustmentInput, StockTransaction, TransactionType } from "@/types/inventory";

export interface InventoryQuery extends PageQuery {
  search?: string;
  lowStockOnly?: boolean;
}

export interface MovementQuery extends PageQuery {
  productId?: number;
  type?: TransactionType;
  /** yyyy-mm-dd, inclusive */
  from?: string;
  to?: string;
}

/** Stock only changes through stock-in and adjustments here (and order issues in the backend). */
export const inventoryService = {
  list: (query: InventoryQuery, signal?: AbortSignal) => api.get<Page<InventoryRow>>("/inventory", { ...query }, signal),
  setReorderLevel: (productId: number, reorderLevel: number, version: number) =>
    api.put<InventoryRow>(`/inventory/${productId}/reorder-level`, { reorderLevel, version }),
  movements: (query: MovementQuery, signal?: AbortSignal) =>
    api.get<Page<StockTransaction>>("/stock-transactions", { ...query }, signal),
  adjust: (input: StockAdjustmentInput) => api.post<StockTransaction>("/stock-adjustments", input),
};
