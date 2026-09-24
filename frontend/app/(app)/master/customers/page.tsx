"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { CustomersPage } from "@/features/master/CustomersPage";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <CustomersPage />
    </RequirePermission>
  );
}
