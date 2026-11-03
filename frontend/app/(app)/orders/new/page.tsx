"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { OrderFormPage } from "@/features/orders/OrderFormPage";

export default function Page() {
  return (
    <RequirePermission permission="ORDER_MANAGE">
      <OrderFormPage />
    </RequirePermission>
  );
}
