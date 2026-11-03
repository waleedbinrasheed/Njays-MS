"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { MeasurementsPage } from "@/features/master/MeasurementsPage";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <MeasurementsPage />
    </RequirePermission>
  );
}
