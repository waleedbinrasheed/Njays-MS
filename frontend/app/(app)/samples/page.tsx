"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { SamplesHistoryPage } from "@/features/samples/SamplesHistoryPage";

export default function Page() {
  return (
    <RequirePermission permission="SAMPLE_VIEW">
      <SamplesHistoryPage />
    </RequirePermission>
  );
}
