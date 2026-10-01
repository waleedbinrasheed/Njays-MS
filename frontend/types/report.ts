import type { ReferenceType, TransactionType } from "@/types/inventory";
import type { DocumentStatus, PaymentStatus } from "@/types/purchase";

export interface ReportPage<T, S> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  summary: S;
}

export interface InventoryRow {
  productId: number;
  isbn: string | null;
  title: string;
  publisher: string | null;
  category: string | null;
  quantity: number;
  reorderLevel: number;
  purchasePrice: number;
  sellingPrice: number;
  stockValue: number;
  active: boolean;
}
export interface InventoryReportSummary {
  products: number;
  units: number;
  stockValue: number;
}

export interface LowStockRow {
  productId: number;
  isbn: string | null;
  title: string;
  publisher: string | null;
  category: string | null;
  quantity: number;
  reorderLevel: number;
  shortfall: number;
  purchasePrice: number;
}
export interface LowStockSummary {
  products: number;
  outOfStock: number;
  unitsToReorder: number;
}

export interface DocumentRow {
  id: number;
  number: string;
  date: string;
  /** Buyer: representative (sales; null on old invoices) or supplier (purchases). */
  partyId: number | null;
  party: string | null;
  /** Sales only: the optional customer. */
  customer: string | null;
  items: number;
  total: number;
  paymentStatus: PaymentStatus;
  status: DocumentStatus;
  createdBy: string;
}
export interface DocumentSummary {
  documents: number;
  total: number;
  paid: number;
  unpaid: number;
  partial: number;
}

export interface RepresentativeRow {
  /** Null for the "Unassigned" row (invoices from before representatives existed). */
  id: number | null;
  name: string;
  area: string | null;
  active: boolean;
  customers: number;
  invoices: number;
  salesTotal: number;
  returns: number;
  netSales: number;
  paid: number;
  owed: number;
  lastSale: string | null;
}
export interface RepresentativeSummary {
  rows: number;
  representatives: number;
  invoices: number;
  salesTotal: number;
  returns: number;
  netSales: number;
  owed: number;
}

export interface MovementRow {
  id: number;
  date: string;
  productId: number;
  product: string;
  isbn: string | null;
  type: TransactionType;
  quantity: number;
  previousQuantity: number;
  resultingQuantity: number;
  referenceType: ReferenceType | null;
  referenceId: number | null;
  reference: string | null;
  user: string;
  notes: string | null;
}
export interface MovementSummary {
  movements: number;
  unitsIn: number;
  unitsOut: number;
}

export interface Figure {
  count: number;
  total: number;
}

export interface RecentDocument {
  id: number;
  number: string;
  date: string;
  party: string;
  total: number;
  status: DocumentStatus;
}

export interface RecentMovement {
  id: number;
  date: string;
  product: string;
  type: TransactionType;
  quantity: number;
  user: string;
}

/** Sections are null when the viewer lacks the matching permission. */
export interface Dashboard {
  today: string;
  totalProducts: number | null;
  totalQuantity: number | null;
  lowStockProducts: number | null;
  outOfStockProducts: number | null;
  todaySales: Figure | null;
  monthSales: Figure | null;
  todayPurchases: Figure | null;
  monthPurchases: Figure | null;
  lowStock: LowStockRow[] | null;
  recentSales: RecentDocument[] | null;
  recentPurchases: RecentDocument[] | null;
  recentMovements: RecentMovement[] | null;
}
