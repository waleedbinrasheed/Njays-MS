import type { AdjustmentReason, TransactionType } from "@/types/inventory";
import type { CalendarDateType, FabricSource, OrderStatus, PaymentKind, PaymentMethod, PaymentStatus } from "@/types/order";

export const ORDER_STATUSES: OrderStatus[] = ["RECEIVED", "CUTTING", "STITCHING", "TRIAL", "READY", "DELIVERED", "CANCELLED"];

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  RECEIVED: "Received",
  CUTTING: "Cutting",
  STITCHING: "Stitching",
  TRIAL: "Trial",
  READY: "Ready",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

/** Statuses in which an order's lines may still be edited. */
export const EDITABLE_STATUSES: OrderStatus[] = ["RECEIVED", "CUTTING", "STITCHING"];

/** The order of the workflow; CANCELLED is outside it. */
export const STATUS_FLOW: OrderStatus[] = ["RECEIVED", "CUTTING", "STITCHING", "TRIAL", "READY", "DELIVERED"];

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  UNPAID: "Unpaid",
  PARTIAL: "Partially paid",
  PAID: "Paid",
};

export const PAYMENT_KIND_LABELS: Record<PaymentKind, string> = {
  ADVANCE: "Advance",
  PAYMENT: "Payment",
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: "Cash",
  BANK: "Bank transfer",
  CARD: "Card",
  OTHER: "Other",
};

export const FABRIC_SOURCE_LABELS: Record<FabricSource, string> = {
  STOCK: "From stock",
  CUSTOMER: "Customer's own fabric",
};

export const CALENDAR_DATE_LABELS: Record<CalendarDateType, string> = {
  DUE: "Due date",
  TRIAL: "Trial",
  DELIVERY: "Delivery",
};

export const ADJUSTMENT_REASON_LABELS: Record<AdjustmentReason, string> = {
  STOCK_IN: "Stock-in (new fabric received)",
  DAMAGED: "Damaged",
  LOST: "Lost",
  COUNT_CORRECTION: "Physical count correction",
  FOUND: "Found",
  OTHER: "Other",
};

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  STOCK_IN: "Stock-in",
  ORDER_ISSUE: "Used in order",
  ORDER_RETURN: "Returned from order",
  ADJUSTMENT: "Adjustment",
};

/** "+5" / "−3" with a real minus sign. */
export function formatSignedQuantity(value: number): string {
  if (value > 0) return `+${value}`;
  if (value < 0) return `−${Math.abs(value)}`;
  return "0";
}

/** Metres to three decimals, without trailing zeros noise: 2.5 -> "2.500". */
export function formatMetres(value: number | null | undefined): string {
  if (value === null || value === undefined) return "-";
  return `${Number(value).toFixed(3)} m`;
}
