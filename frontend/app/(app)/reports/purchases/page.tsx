"use client";

import { Suspense } from "react";
import { PageSpinner } from "@/components/ui/Spinner";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { PurchaseReportPage } from "@/features/reports/ReportPages";

export default function Page() {
  return (
    <RequirePermission permission="REPORT_VIEW">
      <Suspense fallback={<PageSpinner />}>
        <PurchaseReportPage />
      </Suspense>
    </RequirePermission>
  );
}
