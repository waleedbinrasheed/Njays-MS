import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type {
  InventoryItem,
  InventorySummary,
  StockAdjustment,
  StockAdjustmentInput,
  StockStatusFilter,
  StockTransaction,
  TransactionType,
} from "@/types/inventory";

export interface InventoryQuery extends PageQuery {
  search?: string;
  categoryId?: number;
  publisherId?: number;
  status?: StockStatusFilter;
  active?: boolean;
}

export interface MovementQuery extends PageQuery {
  productId?: number;
  type?: TransactionType;
  /** yyyy-mm-dd, inclusive */
  from?: string;
  to?: string;
}

/** Stock can only be changed through adjustments here (and later purchases/sales); there is no direct edit. */
export const inventoryService = {
  list: (query: InventoryQuery, signal?: AbortSignal) =>
    api.get<Page<InventoryItem>>("/inventory", { ...query }, signal),
  get: (productId: number, signal?: AbortSignal) => api.get<InventoryItem>(`/inventory/${productId}`, undefined, signal),
  summary: (signal?: AbortSignal) => api.get<InventorySummary>("/inventory/summary", undefined, signal),
  setReorderLevel: (productId: number, reorderLevel: number) =>
    api.put<InventoryItem>(`/inventory/${productId}/reorder-level`, { reorderLevel }),
  movements: (query: MovementQuery, signal?: AbortSignal) =>
    api.get<Page<StockTransaction>>("/stock-transactions", { ...query }, signal),
  adjust: (input: StockAdjustmentInput) => api.post<StockAdjustment>("/stock-adjustments", input),
  adjustments: (query: PageQuery & { productId?: number }, signal?: AbortSignal) =>
    api.get<Page<StockAdjustment>>("/stock-adjustments", { ...query }, signal),
};
