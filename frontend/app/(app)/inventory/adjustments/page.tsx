"use client";

import { Suspense } from "react";
import { PageSpinner } from "@/components/ui/Spinner";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { StockAdjustmentPage } from "@/features/inventory/StockAdjustmentPage";

export default function Page() {
  return (
    <RequirePermission permission="INVENTORY_VIEW">
      <Suspense fallback={<PageSpinner />}>
        <StockAdjustmentPage />
      </Suspense>
    </RequirePermission>
  );
}
