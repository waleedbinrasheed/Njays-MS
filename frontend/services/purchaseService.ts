import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type {
  BusinessDetails,
  CreatePurchaseInput,
  DocumentStatus,
  PaymentStatus,
  PurchaseDetail,
  PurchaseSummary,
} from "@/types/purchase";

export interface PurchaseQuery extends PageQuery {
  search?: string;
  supplierId?: number;
  productId?: number;
  from?: string;
  to?: string;
  paymentStatus?: PaymentStatus;
  status?: DocumentStatus;
}

export const purchaseService = {
  list: (query: PurchaseQuery, signal?: AbortSignal) =>
    api.get<Page<PurchaseSummary>>("/purchases", { ...query }, signal),
  get: (id: number, signal?: AbortSignal) => api.get<PurchaseDetail>(`/purchases/${id}`, undefined, signal),
  create: (input: CreatePurchaseInput) => api.post<PurchaseDetail>("/purchases", input),
  setPaymentStatus: (id: number, paymentStatus: PaymentStatus) =>
    api.put<PurchaseDetail>(`/purchases/${id}/payment-status`, { paymentStatus }),
  cancel: (id: number, reason: string) => api.post<PurchaseDetail>(`/purchases/${id}/cancel`, { reason }),
};

export const settingsService = {
  business: (signal?: AbortSignal) => api.get<BusinessDetails>("/settings/business", undefined, signal),
};
