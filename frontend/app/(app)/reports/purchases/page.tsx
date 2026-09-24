"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { PurchaseReportPage } from "@/features/reports/ReportPages";

export default function Page() {
  return (
    <RequirePermission permission="REPORT_VIEW">
      <PurchaseReportPage />
    </RequirePermission>
  );
}
