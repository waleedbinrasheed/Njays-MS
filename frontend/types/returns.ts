import type { ProductRef } from "@/types/inventory";
import type { Ref } from "@/types/master";

export type ReturnKind = "sale" | "purchase";

export interface ReturnableLine {
  documentItemId: number;
  lineNo: number;
  product: ProductRef;
  originalQuantity: number;
  returnedQuantity: number;
  returnableQuantity: number;
  lineTotal: number;
  returnedAmount: number;
}

export interface CreateReturnInput {
  documentId: number;
  returnDate: string;
  reason: string;
  notes: string;
  items: { documentItemId: number; quantity: number }[];
}

export interface ReturnLine {
  id: number;
  documentItemId: number;
  product: ProductRef;
  quantity: number;
  lineTotal: number;
}

export interface ReturnSummary {
  id: number;
  returnNumber: string;
  returnDate: string;
  documentId: number;
  documentNumber: string;
  party: Ref;
  total: number;
  createdBy: Ref | null;
  createdAt: string;
}

export interface ReturnDetail extends Omit<ReturnSummary, "createdBy"> {
  documentDate: string;
  reason: string;
  notes: string | null;
  items: ReturnLine[];
  createdBy: Ref | null;
}
