"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { NewSampleReturnPage } from "@/features/samples/NewSampleReturnPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const n = Number(id);
  return (
    <RequirePermission permission="SAMPLE_MANAGE">
      {Number.isInteger(n) && n > 0 ? <NewSampleReturnPage key={n} sampleId={n} /> : <EmptyState title="Sample not found" />}
    </RequirePermission>
  );
}
