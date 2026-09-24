"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { StockMovementReportPage } from "@/features/reports/ReportPages";

export default function Page() {
  return (
    <RequirePermission permission="REPORT_VIEW">
      <StockMovementReportPage />
    </RequirePermission>
  );
}
