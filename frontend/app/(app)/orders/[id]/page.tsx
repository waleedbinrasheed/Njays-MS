"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { OrderDetailPage } from "@/features/orders/OrderDetailPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const orderId = Number(id);
  return (
    <RequirePermission permission="ORDER_VIEW">
      {Number.isInteger(orderId) && orderId > 0 ? <OrderDetailPage key={orderId} id={orderId} /> : <EmptyState title="Order not found" />}
    </RequirePermission>
  );
}
