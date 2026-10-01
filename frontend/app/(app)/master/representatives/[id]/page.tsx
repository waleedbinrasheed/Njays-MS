"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { RepresentativeDetailPage } from "@/features/master/RepresentativeDetailPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const n = Number(id);
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      {Number.isInteger(n) && n > 0 ? <RepresentativeDetailPage key={n} id={n} /> : <EmptyState title="Not found" />}
    </RequirePermission>
  );
}
