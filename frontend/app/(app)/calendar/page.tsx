"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { CalendarPage } from "@/features/calendar/CalendarPage";

export default function Page() {
  return (
    <RequirePermission permission="ORDER_VIEW">
      <CalendarPage />
    </RequirePermission>
  );
}
