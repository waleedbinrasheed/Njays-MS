import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type { CalendarEntry, Order, OrderInput, OrderPayment, OrderStatus, OrderSummary, PaymentKind, PaymentMethod } from "@/types/order";

export interface OrderQuery extends PageQuery {
  search?: string;
  status?: OrderStatus;
  customerId?: number;
  dueFrom?: string;
  dueTo?: string;
}

export interface PaymentInput {
  kind: PaymentKind;
  amount: number;
  paidOn: string;
  method: PaymentMethod;
  note: string;
}

export const orderService = {
  list: (query: OrderQuery, signal?: AbortSignal) => api.get<Page<OrderSummary>>("/orders", { ...query }, signal),
  get: (id: number, signal?: AbortSignal) => api.get<Order>(`/orders/${id}`, undefined, signal),
  create: (input: OrderInput) => api.post<Order>("/orders", input),
  update: (id: number, input: OrderInput) => api.put<Order>(`/orders/${id}`, input),
  changeStatus: (id: number, status: OrderStatus, note: string, version: number) =>
    api.post<Order>(`/orders/${id}/status`, { status, note, version }),
  cancel: (id: number, reason: string, version: number) => api.post<Order>(`/orders/${id}/cancel`, { reason, version }),
  recordPayment: (id: number, input: PaymentInput) => api.post<OrderPayment>(`/orders/${id}/payments`, input),
  calendar: (from: string, to: string, signal?: AbortSignal) =>
    api.get<CalendarEntry[]>("/orders/calendar", { from, to }, signal),
};
