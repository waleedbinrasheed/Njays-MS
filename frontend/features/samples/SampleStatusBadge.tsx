import { Badge } from "@/components/ui/Feedback";
import type { SampleStatus } from "@/types/sample";
import { SAMPLE_STATUS_LABELS } from "@/utils/labels";

const TONE = { ISSUED: "blue", PARTIALLY_RETURNED: "amber", RETURNED: "green", CANCELLED: "gray" } as const;

export function SampleStatusBadge({ status }: { status: SampleStatus }) {
  return <Badge tone={TONE[status]}>{SAMPLE_STATUS_LABELS[status]}</Badge>;
}

/** The marking every sample document carries, on screen and on paper. */
export function SampleSaleBanner() {
  return (
    <div className="rounded-md border-2 border-amber-500 bg-amber-50 px-4 py-2 text-center print:bg-white">
      <p className="text-lg font-bold uppercase tracking-widest text-amber-800 print:text-slate-900">Sample Sale</p>
      <p className="text-xs font-medium uppercase tracking-wide text-amber-700 print:text-slate-700">Not Retail Sale</p>
    </div>
  );
}
