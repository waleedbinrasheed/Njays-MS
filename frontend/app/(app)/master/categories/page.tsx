"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { CategoriesPage } from "@/features/master/CategoriesPage";

export default function Page() {
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      <CategoriesPage />
    </RequirePermission>
  );
}
