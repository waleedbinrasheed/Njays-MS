"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { SuppliersPage } from "@/features/master/PartyPages";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <SuppliersPage />
    </RequirePermission>
  );
}
