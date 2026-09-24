"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { ProductsPage } from "@/features/master/ProductsPage";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <ProductsPage />
    </RequirePermission>
  );
}
