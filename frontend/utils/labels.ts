import type { AdjustmentReason, StockLevel, TransactionType } from "@/types/inventory";
import type { DocumentStatus, PaymentStatus } from "@/types/purchase";
import type { SampleStatus } from "@/types/sample";

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  PURCHASE: "Purchase",
  SALE: "Sale",
  SALE_RETURN: "Sales return",
  PURCHASE_RETURN: "Purchase return",
  ADJUSTMENT: "Adjustment",
  PURCHASE_CANCEL: "Purchase cancelled",
  SALE_CANCEL: "Sale cancelled",
  SAMPLE_ISSUE: "Sample issue",
  SAMPLE_RETURN: "Sample return",
  SAMPLE_CANCEL: "Sample cancelled",
};

export const ADJUSTMENT_REASON_LABELS: Record<AdjustmentReason, string> = {
  DAMAGED: "Damaged",
  LOST: "Lost",
  COUNT_CORRECTION: "Physical count correction",
  FOUND: "Found",
  OTHER: "Other",
};

export const STOCK_LEVEL_LABELS: Record<StockLevel, string> = {
  OUT_OF_STOCK: "Out of stock",
  LOW: "Low stock",
  OK: "In stock",
};

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  UNPAID: "Unpaid",
  PARTIAL: "Partially paid",
  PAID: "Paid",
};

export const DOCUMENT_STATUS_LABELS: Record<DocumentStatus, string> = {
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export const SAMPLE_STATUS_LABELS: Record<SampleStatus, string> = {
  ISSUED: "Issued",
  PARTIALLY_RETURNED: "Partially returned",
  RETURNED: "Fully returned",
  CANCELLED: "Cancelled",
};

/** "+5" / "−3" with a real minus sign. */
export function formatSignedQuantity(value: number): string {
  if (value > 0) return `+${value}`;
  if (value < 0) return `−${Math.abs(value)}`;
  return "0";
}
