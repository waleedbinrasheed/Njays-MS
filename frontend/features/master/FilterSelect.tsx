"use client";

import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";

/** Compact type-to-search picker for filter bars (label kept for screen readers only). */
export function FilterSelect({
  placeholder,
  ...props
}: {
  label: string;
  placeholder: string;
  value: Option | null;
  onChange: (o: Option | null) => void;
  load: (s: string, signal: AbortSignal) => Promise<Option[]>;
}) {
  return (
    <div className="[&_label]:sr-only">
      <AsyncSelect {...props} placeholder={placeholder} />
    </div>
  );
}
