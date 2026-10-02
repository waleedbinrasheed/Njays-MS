"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { NewSamplePage } from "@/features/samples/NewSamplePage";

export default function Page() {
  return (
    <RequirePermission permission="SAMPLE_MANAGE">
      <NewSamplePage />
    </RequirePermission>
  );
}
