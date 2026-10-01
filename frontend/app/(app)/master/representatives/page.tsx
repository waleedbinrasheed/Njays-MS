"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { RepresentativesPage } from "@/features/master/RepresentativesPage";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <RepresentativesPage />
    </RequirePermission>
  );
}
