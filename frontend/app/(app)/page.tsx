"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { DashboardPage } from "@/features/dashboard/DashboardPage";

export default function Page() {
  return (
    <RequirePermission permission="DASHBOARD_VIEW">
      <DashboardPage />
    </RequirePermission>
  );
}
