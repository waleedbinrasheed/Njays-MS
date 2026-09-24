"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { NewPurchasePage } from "@/features/purchases/NewPurchasePage";

export default function Page() {
  return (
    <RequirePermission permission="PURCHASE_CREATE">
      <NewPurchasePage />
    </RequirePermission>
  );
}
