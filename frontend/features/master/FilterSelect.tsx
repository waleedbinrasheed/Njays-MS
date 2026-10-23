"use client";

import { AsyncSelect, type Option, type OptionLoader } from "@/components/ui/AsyncSelect";

/** Compact type-to-search picker for filter bars (label kept for screen readers only). */
export function FilterSelect({
  placeholder,
  ...props
}: {
  label: string;
  placeholder: string;
  value: Option | null;
  onChange: (o: Option | null) => void;
  load: OptionLoader;
}) {
  return (
    <div className="[&_label]:sr-only">
      <AsyncSelect {...props} placeholder={placeholder} />
    </div>
  );
}
