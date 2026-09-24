import type { ProductRef } from "@/types/inventory";
import type { Ref } from "@/types/master";

export type PaymentStatus = "PAID" | "UNPAID" | "PARTIAL";
export type DocumentStatus = "COMPLETED" | "CANCELLED";

export interface PurchaseLineInput {
  productId: number;
  quantity: number;
  unitCost: number;
  discountPercent: number;
  taxPercent: number;
}

export interface CreatePurchaseInput {
  supplierId: number;
  purchaseDate: string;
  supplierInvoiceNumber: string;
  paymentStatus: PaymentStatus;
  invoiceDiscount: number;
  notes: string;
  items: PurchaseLineInput[];
}

export interface PurchaseLine {
  id: number;
  lineNo: number;
  product: ProductRef;
  quantity: number;
  unitCost: number;
  discountPercent: number;
  discountAmount: number;
  taxPercent: number;
  taxAmount: number;
  lineTotal: number;
}

export interface PurchaseSummary {
  id: number;
  purchaseNumber: string;
  purchaseDate: string;
  supplier: Ref;
  supplierInvoiceNumber: string | null;
  total: number;
  paymentStatus: PaymentStatus;
  status: DocumentStatus;
  createdBy: Ref | null;
  createdAt: string;
}

export interface PurchaseDetail extends Omit<PurchaseSummary, "createdAt"> {
  subtotal: number;
  invoiceDiscount: number;
  discountTotal: number;
  taxTotal: number;
  notes: string | null;
  items: PurchaseLine[];
  createdAt: string;
  cancelledAt: string | null;
  cancelledBy: Ref | null;
  cancelReason: string | null;
  version: number;
}

export interface BusinessDetails {
  name: string;
  address: string | null;
  phone: string | null;
  email: string | null;
}
