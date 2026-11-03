import type { OrderStatus, PaymentStatus } from "@/types/order";

export interface Dashboard {
  today: string;
  dueToday: number | null;
  overdue: number | null;
  readyForDelivery: number | null;
  inProgress: Record<"RECEIVED" | "CUTTING" | "STITCHING" | "TRIAL", number> | null;
  outstandingBalance: number | null;
  ordersToday: number | null;
  recentOrders: { id: number; orderNumber: string; customerName: string; status: OrderStatus; dueDate: string }[] | null;
  lowStockFabrics: number | null;
}

export interface OrderReportRow {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  customerName: string;
  orderDate: string;
  dueDate: string;
  total: number;
  paid: number;
  balance: number;
  paymentStatus: PaymentStatus;
  lineCount: number;
}

export interface OrderReportPage {
  content: OrderReportRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  totals: { orders: number; total: number; paid: number; balance: number };
}

export interface FabricStockRow {
  productId: number;
  code: string;
  name: string;
  colour: string | null;
  quantityOnHand: number;
  reorderLevel: number;
  costPerMetre: number;
  costValue: number;
  lowStock: boolean;
  active: boolean;
}

export interface FabricStockPage {
  content: FabricStockRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  totals: { costValue: number };
}
