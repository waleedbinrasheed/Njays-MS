"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { GarmentTypesPage } from "@/features/master/GarmentTypesPage";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <GarmentTypesPage />
    </RequirePermission>
  );
}
