"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { StockAdjustmentPage } from "@/features/inventory/StockAdjustmentPage";

export default function Page() {
  return (
    <RequirePermission permission="INVENTORY_VIEW">
      <StockAdjustmentPage />
    </RequirePermission>
  );
}
