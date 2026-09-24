import type { ProductRef } from "@/types/inventory";
import type { Ref } from "@/types/master";
import type { DocumentStatus, PaymentStatus } from "@/types/purchase";

export interface SaleLineInput {
  productId: number;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  taxPercent: number;
}

export interface CreateSaleInput {
  /** Omit for the walk-in customer. */
  customerId: number | null;
  saleDate: string;
  paymentStatus: PaymentStatus;
  invoiceDiscount: number;
  notes: string;
  items: SaleLineInput[];
}

export interface SaleLine {
  id: number;
  lineNo: number;
  product: ProductRef;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  discountAmount: number;
  taxPercent: number;
  taxAmount: number;
  lineTotal: number;
}

export interface SaleSummary {
  id: number;
  invoiceNumber: string;
  saleDate: string;
  customer: Ref;
  total: number;
  paymentStatus: PaymentStatus;
  status: DocumentStatus;
  createdBy: Ref | null;
  createdAt: string;
}

export interface SaleDetail extends SaleSummary {
  customerPhone: string | null;
  customerAddress: string | null;
  subtotal: number;
  invoiceDiscount: number;
  discountTotal: number;
  taxTotal: number;
  notes: string | null;
  items: SaleLine[];
  cancelledAt: string | null;
  cancelledBy: Ref | null;
  cancelReason: string | null;
  version: number;
}
