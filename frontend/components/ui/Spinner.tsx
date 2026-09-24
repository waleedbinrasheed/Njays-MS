export function Spinner({ size = "md", label }: { size?: "sm" | "md" | "lg"; label?: string }) {
  const px = { sm: "h-3.5 w-3.5 border-2", md: "h-5 w-5 border-2", lg: "h-8 w-8 border-[3px]" }[size];
  return (
    <span className="inline-flex items-center gap-2" role="status" aria-live="polite">
      <span className={`${px} animate-spin rounded-full border-current border-t-transparent opacity-70`} aria-hidden />
      {label ? <span className="text-sm text-slate-500">{label}</span> : <span className="sr-only">Loading</span>}
    </span>
  );
}

export function PageSpinner({ label = "Loading..." }: { label?: string }) {
  return (
    <div className="flex flex-1 items-center justify-center py-16 text-blue-600">
      <Spinner size="lg" label={label} />
    </div>
  );
}
