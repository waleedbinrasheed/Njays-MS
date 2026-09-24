const pkr = new Intl.NumberFormat("en-PK", {
  style: "currency",
  currency: "PKR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatCurrency(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "-";
  return pkr.format(Number(value));
}

/** Today's date in the shop's time zone as yyyy-mm-dd (for date inputs and API filters). */
export function todayIso(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Karachi" }).format(new Date());
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "-";
  // Plain dates (yyyy-mm-dd) are calendar days, not instants: never shift them by time zone.
  const plain = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const date = plain ? new Date(Number(plain[1]), Number(plain[2]) - 1, Number(plain[3])) : new Date(value);
  return date.toLocaleDateString("en-PK", { year: "numeric", month: "short", day: "2-digit" });
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  return new Date(value).toLocaleString("en-PK", {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}
