"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { ReturnHistoryPage } from "@/features/returns/ReturnHistoryPage";

export default function Page() {
  return (
    <RequirePermission permission="SALE_VIEW">
      <ReturnHistoryPage kind="sale" />
    </RequirePermission>
  );
}
