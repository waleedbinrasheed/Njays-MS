import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type { DocumentStatus, PaymentStatus } from "@/types/purchase";
import type { CreateSaleInput, SaleDetail, SaleSummary } from "@/types/sale";

export interface SaleQuery extends PageQuery {
  search?: string;
  representativeId?: number;
  customerId?: number;
  productId?: number;
  from?: string;
  to?: string;
  paymentStatus?: PaymentStatus;
  status?: DocumentStatus;
  createdBy?: number;
}

export const saleService = {
  list: (query: SaleQuery, signal?: AbortSignal) => api.get<Page<SaleSummary>>("/sales", { ...query }, signal),
  get: (id: number, signal?: AbortSignal) => api.get<SaleDetail>(`/sales/${id}`, undefined, signal),
  create: (input: CreateSaleInput) => api.post<SaleDetail>("/sales", input),
  setPaymentStatus: (id: number, paymentStatus: PaymentStatus) =>
    api.put<SaleDetail>(`/sales/${id}/payment-status`, { paymentStatus }),
  cancel: (id: number, reason: string) => api.post<SaleDetail>(`/sales/${id}/cancel`, { reason }),
};
