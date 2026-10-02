import type { ProductRef } from "@/types/inventory";
import type { Ref, RepresentativeRef } from "@/types/master";

/** Worked out by the backend from the returns recorded. */
export type SampleStatus = "ISSUED" | "PARTIALLY_RETURNED" | "RETURNED" | "CANCELLED";

export interface SampleLineInput {
  productId: number;
  quantity: number;
  /** Defaults to the book's purchase price; never above its selling price. */
  unitPrice: number;
}

export interface CreateSampleInput {
  representativeId: number;
  issueDate: string;
  notes: string;
  items: SampleLineInput[];
}

export interface SampleLine {
  id: number;
  lineNo: number;
  product: ProductRef;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  returnedQuantity: number;
  returnedAmount: number;
  outstandingQuantity: number;
  outstandingAmount: number;
}

export interface SampleSummary {
  id: number;
  sampleNumber: string;
  issueDate: string;
  representative: RepresentativeRef;
  totalQuantity: number;
  total: number;
  returnedQuantity: number;
  returnedTotal: number;
  outstandingQuantity: number;
  outstandingTotal: number;
  status: SampleStatus;
  createdBy: Ref | null;
  createdAt: string;
}

export interface SampleReturnRef {
  id: number;
  returnNumber: string;
  returnDate: string;
  totalQuantity: number;
  total: number;
}

export interface SampleDetail extends SampleSummary {
  representativeAddress: string | null;
  notes: string | null;
  items: SampleLine[];
  returns: SampleReturnRef[];
  cancelledAt: string | null;
  cancelledBy: Ref | null;
  cancelReason: string | null;
}

export interface CreateSampleReturnInput {
  sampleId: number;
  returnDate: string;
  notes: string;
  items: { sampleItemId: number; quantity: number }[];
}

export interface SampleReturnLine {
  id: number;
  sampleItemId: number;
  product: ProductRef;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface SampleReturnSummary {
  id: number;
  returnNumber: string;
  returnDate: string;
  sampleId: number;
  sampleNumber: string;
  representative: Ref;
  totalQuantity: number;
  total: number;
  createdBy: Ref | null;
  createdAt: string;
}

export interface SampleReturnDetail {
  id: number;
  returnNumber: string;
  returnDate: string;
  sampleId: number;
  sampleNumber: string;
  issueDate: string;
  representative: RepresentativeRef;
  totalQuantity: number;
  total: number;
  notes: string | null;
  items: SampleReturnLine[];
  createdBy: Ref | null;
  createdAt: string;
}

/** Sample report / representative sample history: one issued book. */
export interface SampleLineRow {
  sampleId: number;
  itemId: number;
  sampleNumber: string;
  issueDate: string;
  representativeId: number;
  representative: string;
  productId: number;
  title: string;
  isbn: string | null;
  issuedQuantity: number;
  unitPrice: number;
  issuedAmount: number;
  returnedQuantity: number;
  returnedAmount: number;
  outstandingQuantity: number;
  outstandingAmount: number;
  status: SampleStatus;
}

export interface SampleRepresentativeRow {
  id: number;
  name: string;
  area: string | null;
  active: boolean;
  samples: number;
  issuedQuantity: number;
  issuedAmount: number;
  returnedQuantity: number;
  returnedAmount: number;
  outstandingQuantity: number;
  outstandingAmount: number;
  lastIssue: string | null;
}

/** Cancelled samples are listed but not counted. */
export interface SampleReportSummary {
  rows: number;
  samples: number;
  issuedQuantity: number;
  issuedAmount: number;
  returnedQuantity: number;
  returnedAmount: number;
  outstandingQuantity: number;
  outstandingAmount: number;
}
