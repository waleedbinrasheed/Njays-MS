import { Badge } from "@/components/ui/Feedback";
import type { OrderStatus, PaymentStatus } from "@/types/order";
import { ORDER_STATUS_LABELS, PAYMENT_STATUS_LABELS } from "@/utils/labels";

const STATUS_TONE: Record<OrderStatus, "gray" | "blue" | "amber" | "green" | "red"> = {
  RECEIVED: "gray",
  CUTTING: "blue",
  STITCHING: "blue",
  TRIAL: "amber",
  READY: "green",
  DELIVERED: "green",
  CANCELLED: "red",
};

const PAYMENT_TONE = { PAID: "green", PARTIAL: "amber", UNPAID: "red" } as const;

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{ORDER_STATUS_LABELS[status]}</Badge>;
}

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge tone={PAYMENT_TONE[status]}>{PAYMENT_STATUS_LABELS[status]}</Badge>;
}
