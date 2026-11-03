import { api } from "@/services/apiClient";
import type { FabricStockPage, OrderReportPage, Dashboard } from "@/types/report";
import type { OrderStatus } from "@/types/order";

export interface OrderReportQuery {
  from?: string;
  to?: string;
  status?: OrderStatus;
  customerId?: number;
  page: number;
  size: number;
  sort: string;
}

export const reportService = {
  dashboard: (signal?: AbortSignal) => api.get<Dashboard>("/dashboard", undefined, signal),
  orders: (query: OrderReportQuery, signal?: AbortSignal) => api.get<OrderReportPage>("/reports/orders", { ...query }, signal),
  fabricStock: (query: { search?: string; lowStockOnly?: boolean; page: number; size: number; sort: string }, signal?: AbortSignal) =>
    api.get<FabricStockPage>("/reports/fabric-stock", { ...query }, signal),
};
