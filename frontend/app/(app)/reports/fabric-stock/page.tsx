"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { FabricStockReportPage } from "@/features/reports/FabricStockReportPage";

export default function Page() {
  return (
    <RequirePermission permission="REPORT_VIEW">
      <FabricStockReportPage />
    </RequirePermission>
  );
}
