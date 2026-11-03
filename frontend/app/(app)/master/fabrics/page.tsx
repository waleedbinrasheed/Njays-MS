"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { FabricsPage } from "@/features/master/FabricsPage";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <FabricsPage />
    </RequirePermission>
  );
}
