"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { OrderFormPage } from "@/features/orders/OrderFormPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const orderId = Number(id);
  return (
    <RequirePermission permission="ORDER_MANAGE">
      {Number.isInteger(orderId) && orderId > 0 ? <OrderFormPage key={orderId} orderId={orderId} /> : <EmptyState title="Order not found" />}
    </RequirePermission>
  );
}
