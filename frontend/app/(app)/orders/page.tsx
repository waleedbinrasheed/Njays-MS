"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { OrdersPage } from "@/features/orders/OrdersPage";

export default function Page() {
  return (
    <RequirePermission permission="ORDER_VIEW">
      <OrdersPage />
    </RequirePermission>
  );
}
