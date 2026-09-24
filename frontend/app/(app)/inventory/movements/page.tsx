"use client";

import { Suspense } from "react";
import { PageSpinner } from "@/components/ui/Spinner";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { StockMovementsPage } from "@/features/inventory/StockMovementsPage";

export default function Page() {
  return (
    <RequirePermission permission="INVENTORY_VIEW">
      <Suspense fallback={<PageSpinner />}>
        <StockMovementsPage />
      </Suspense>
    </RequirePermission>
  );
}
