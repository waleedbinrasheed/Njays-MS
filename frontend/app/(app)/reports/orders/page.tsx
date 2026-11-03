"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { OrdersReportPage } from "@/features/reports/OrdersReportPage";

export default function Page() {
  return (
    <RequirePermission permission="REPORT_VIEW">
      <OrdersReportPage />
    </RequirePermission>
  );
}
