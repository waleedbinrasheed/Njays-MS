"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { SalesHistoryPage } from "@/features/sales/SalesHistoryPage";

export default function Page() {
  return (
    <RequirePermission permission="SALE_VIEW">
      <SalesHistoryPage />
    </RequirePermission>
  );
}
