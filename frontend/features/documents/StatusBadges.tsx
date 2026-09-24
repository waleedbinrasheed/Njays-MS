import { Badge } from "@/components/ui/Feedback";
import type { DocumentStatus, PaymentStatus } from "@/types/purchase";
import { DOCUMENT_STATUS_LABELS, PAYMENT_STATUS_LABELS } from "@/utils/labels";

const PAYMENT_TONE = { PAID: "green", PARTIAL: "amber", UNPAID: "red" } as const;

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge tone={PAYMENT_TONE[status]}>{PAYMENT_STATUS_LABELS[status]}</Badge>;
}

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  return status === "CANCELLED" ? <Badge tone="gray">{DOCUMENT_STATUS_LABELS[status]}</Badge> : null;
}
