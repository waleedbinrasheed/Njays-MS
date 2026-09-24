"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { UsersPage } from "@/features/users/UsersPage";

export default function Page() {
  return (
    <RequirePermission permission="USER_MANAGE">
      <UsersPage />
    </RequirePermission>
  );
}
