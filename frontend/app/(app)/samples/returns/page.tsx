"use client";

import { RequirePermission } from "@/features/auth/AuthGuard";
import { SampleReturnsPage } from "@/features/samples/SampleReturnsPage";

export default function Page() {
  return (
    <RequirePermission permission="SAMPLE_VIEW">
      <SampleReturnsPage />
    </RequirePermission>
  );
}
