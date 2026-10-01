"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { RepresentativeReportPage } from "@/features/reports/ReportPages";

export default function Page() {
  return (
    <RequirePermission permission="REPORT_VIEW">
      <RepresentativeReportPage />
    </RequirePermission>
  );
}
