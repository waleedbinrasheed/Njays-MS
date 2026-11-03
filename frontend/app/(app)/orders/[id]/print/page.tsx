"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { PrintOrderPage } from "@/features/orders/PrintOrderPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const orderId = Number(id);
  return (
    <RequirePermission permission="ORDER_VIEW">
      {Number.isInteger(orderId) && orderId > 0 ? <PrintOrderPage key={orderId} id={orderId} /> : <EmptyState title="Order not found" />}
    </RequirePermission>
  );
}
