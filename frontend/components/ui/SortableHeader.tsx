export interface SortState {
  field: string;
  direction: "asc" | "desc";
}

export function toSortParam(sort: SortState): string {
  return `${sort.field},${sort.direction}`;
}

export function nextSort(current: SortState, field: string): SortState {
  if (current.field !== field) return { field, direction: "asc" };
  return { field, direction: current.direction === "asc" ? "desc" : "asc" };
}

interface SortableHeaderProps {
  label: string;
  field: string;
  sort: SortState;
  onSort: (field: string) => void;
}

export function SortableHeader({ label, field, sort, onSort }: SortableHeaderProps) {
  const active = sort.field === field;
  return (
    <th scope="col" aria-sort={active ? (sort.direction === "asc" ? "ascending" : "descending") : "none"} className="px-4 py-2.5">
      <button type="button" onClick={() => onSort(field)} className="inline-flex items-center gap-1 font-medium hover:text-slate-900">
        {label}
        <span className={active ? "text-slate-700" : "text-slate-300"} aria-hidden>
          {active && sort.direction === "desc" ? "▼" : "▲"}
        </span>
      </button>
    </th>
  );
}
