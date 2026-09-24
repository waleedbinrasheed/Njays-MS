"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { PublishersPage } from "@/features/master/PartyPages";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <PublishersPage />
    </RequirePermission>
  );
}
