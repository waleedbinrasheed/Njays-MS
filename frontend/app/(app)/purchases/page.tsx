"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { PurchaseHistoryPage } from "@/features/purchases/PurchaseHistoryPage";

export default function Page() {
  return (
    <RequirePermission permission="PURCHASE_VIEW">
      <PurchaseHistoryPage />
    </RequirePermission>
  );
}
