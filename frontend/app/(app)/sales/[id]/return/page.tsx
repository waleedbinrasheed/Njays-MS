"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { NewReturnPage } from "@/features/returns/NewReturnPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const n = Number(id);
  return (
    <RequirePermission permission="RETURN_CREATE">
      {Number.isInteger(n) && n > 0 ? <NewReturnPage key={n} kind="sale" documentId={n} /> : <EmptyState title="Not found" />}
    </RequirePermission>
  );
}
