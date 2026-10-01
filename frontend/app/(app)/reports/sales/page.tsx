"use client";

import { Suspense } from "react";
import { PageSpinner } from "@/components/ui/Spinner";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { SalesReportPage } from "@/features/reports/ReportPages";

export default function Page() {
  return (
    <RequirePermission permission="REPORT_VIEW">
      <Suspense fallback={<PageSpinner />}>
        <SalesReportPage />
      </Suspense>
    </RequirePermission>
  );
}
