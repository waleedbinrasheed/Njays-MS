"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { SampleReportPage } from "@/features/reports/ReportPages";

export default function Page() {
  return (
    <RequirePermission permission="SAMPLE_VIEW">
      <SampleReportPage />
    </RequirePermission>
  );
}
