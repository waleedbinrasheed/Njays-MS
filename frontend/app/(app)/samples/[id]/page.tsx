"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { SampleDetailPage } from "@/features/samples/SampleDetailPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const n = Number(id);
  return (
    <RequirePermission permission="SAMPLE_VIEW">
      {Number.isInteger(n) && n > 0 ? <SampleDetailPage key={n} id={n} /> : <EmptyState title="Sample not found" />}
    </RequirePermission>
  );
}
