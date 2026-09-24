"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { InventoryPage } from "@/features/inventory/InventoryPage";

export default function Page() {
  return (
    <RequirePermission permission="INVENTORY_VIEW">
      <InventoryPage />
    </RequirePermission>
  );
}
