import type { GarmentShape } from "@/types/master";

export type OrderStatus = "RECEIVED" | "CUTTING" | "STITCHING" | "TRIAL" | "READY" | "DELIVERED" | "CANCELLED";

export type PaymentStatus = "UNPAID" | "PARTIAL" | "PAID";

export type FabricSource = "STOCK" | "CUSTOMER";

export type PaymentKind = "ADVANCE" | "PAYMENT";

export type PaymentMethod = "CASH" | "BANK" | "CARD" | "OTHER";

export type CalendarDateType = "DUE" | "TRIAL" | "DELIVERY";

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  customerName: string;
  dueDate: string;
  total: number;
  balance: number;
  paymentStatus: PaymentStatus;
  lineCount: number;
}

export interface OrderLineMeasurement {
  label: string;
  value: number;
  sortOrder: number;
}

export interface OrderLine {
  id: number;
  lineNo: number;
  garmentType: { id: number; name: string; shape: GarmentShape };
  fabricSource: FabricSource;
  fabric: { id: number; code: string; name: string; colour: string | null } | null;
  fabricMetres: number | null;
  customerFabricNote: string | null;
  fabricPricePerMetre: number | null;
  fabricAmount: number;
  stitchingCharge: number;
  lineTotal: number;
  measurementRecordId: number | null;
  measurements: OrderLineMeasurement[];
  notes: string | null;
}

export interface OrderPayment {
  id: number;
  kind: PaymentKind;
  amount: number;
  paidOn: string;
  method: PaymentMethod;
  note: string | null;
  recordedBy: string;
}

export interface OrderStatusChange {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  changedAt: string;
  changedBy: string;
  note: string | null;
}

export interface Order {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  customer: { id: number; name: string; phone: string | null; address: string | null };
  orderDate: string;
  dueDate: string;
  trialDate: string | null;
  deliveryDate: string | null;
  notes: string | null;
  total: number;
  paid: number;
  balance: number;
  paymentStatus: PaymentStatus;
  cancelReason: string | null;
  lines: OrderLine[];
  payments: OrderPayment[];
  statusHistory: OrderStatusChange[];
  createdAt: string;
  createdBy: string;
  version: number;
}

export interface OrderLineInput {
  garmentTypeId: number | null;
  fabricSource: FabricSource;
  fabricProductId: number | null;
  fabricMetres: number | null;
  customerFabricNote: string | null;
  stitchingCharge: number | null;
  measurementRecordId: number | null;
  notes: string | null;
}

export interface OrderInput {
  customerId: number;
  orderDate: string;
  dueDate: string;
  trialDate: string | null;
  deliveryDate: string | null;
  notes: string | null;
  lines: OrderLineInput[];
  version?: number;
}

export interface CalendarEntry {
  orderId: number;
  orderNumber: string;
  customerName: string;
  date: string;
  dateType: CalendarDateType;
  status: OrderStatus;
}
