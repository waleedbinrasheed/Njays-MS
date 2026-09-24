"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { NewSalePage } from "@/features/sales/NewSalePage";

export default function Page() {
  return (
    <RequirePermission permission="SALE_CREATE">
      <NewSalePage />
    </RequirePermission>
  );
}
