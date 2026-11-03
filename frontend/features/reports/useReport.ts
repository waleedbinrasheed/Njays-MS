"use client";

import { useState } from "react";
import { nextSort, toSortParam, type SortState } from "@/components/ui/SortableHeader";
import { useApiQuery } from "@/hooks/useApiQuery";

type Filters = Record<string, string | number | boolean | undefined>;

/**
 * Paging, sorting and loading for a report. Changing filters, sort or page size goes back to page 1.
 * {@code filters} must be JSON-serialisable.
 */
export function useReport<R>(
  fetch: (query: Filters & { page: number; size: number; sort: string }, signal: AbortSignal) => Promise<R>,
  filters: Filters,
  defaultSort: SortState,
  blocked = false,
) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [sort, setSort] = useState<SortState>(defaultSort);

  const filterKey = JSON.stringify(filters);
  const [seenKey, setSeenKey] = useState(filterKey);
  if (seenKey !== filterKey) {
    setSeenKey(filterKey);
    setPage(0);
  }

  const query = useApiQuery(
    (signal) => (blocked ? Promise.resolve(undefined) : fetch({ ...filters, page, size, sort: toSortParam(sort) }, signal)),
    [filterKey, page, size, sort, blocked],
  );

  return {
    ...query,
    page,
    setPage,
    size,
    setSize: (n: number) => {
      setSize(n);
      setPage(0);
    },
    sort,
    onSort: (field: string) => {
      setSort((s) => nextSort(s, field));
      setPage(0);
    },
  };
}

/** "Customer: Ali · From 2026-09-01" for the printed header. */
export function describeFilters(parts: [string, string | null | undefined][]): string {
  return parts
    .filter(([, v]) => v)
    .map(([k, v]) => `${k}: ${v}`)
    .join(" · ");
}
