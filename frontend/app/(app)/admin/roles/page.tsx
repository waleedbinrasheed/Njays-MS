"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { RolesPage } from "@/features/roles/RolesPage";

export default function Page() {
  return (
    <RequirePermission permission="ROLE_MANAGE">
      <RolesPage />
    </RequirePermission>
  );
}
