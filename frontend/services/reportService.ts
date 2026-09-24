import { api } from "@/services/apiClient";
import type { PageQuery } from "@/types/api";
import type {
  Dashboard,
  DocumentRow,
  DocumentSummary,
  InventoryReportSummary,
  InventoryRow,
  LowStockRow,
  LowStockSummary,
  MovementRow,
  MovementSummary,
  ReportPage,
} from "@/types/report";

type Query = PageQuery & Record<string, string | number | boolean | undefined>;

export const reportService = {
  dashboard: (signal?: AbortSignal) => api.get<Dashboard>("/dashboard", undefined, signal),
  inventory: (q: Query, signal?: AbortSignal) =>
    api.get<ReportPage<InventoryRow, InventoryReportSummary>>("/reports/inventory", q, signal),
  lowStock: (q: Query, signal?: AbortSignal) =>
    api.get<ReportPage<LowStockRow, LowStockSummary>>("/reports/low-stock", q, signal),
  sales: (q: Query, signal?: AbortSignal) => api.get<ReportPage<DocumentRow, DocumentSummary>>("/reports/sales", q, signal),
  purchases: (q: Query, signal?: AbortSignal) =>
    api.get<ReportPage<DocumentRow, DocumentSummary>>("/reports/purchases", q, signal),
  stockMovement: (q: Query, signal?: AbortSignal) =>
    api.get<ReportPage<MovementRow, MovementSummary>>("/reports/stock-movement", q, signal),
};
