import { Button } from "@/components/ui/Button";

interface PaginationProps {
  page: number; // zero-based
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, size, totalElements, totalPages, onPageChange }: PaginationProps) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  return (
    <div className="flex flex-col items-center justify-between gap-2 border-t border-slate-200 px-4 py-3 text-sm text-slate-600 sm:flex-row">
      <span>
        Showing <strong>{from}</strong>–<strong>{to}</strong> of <strong>{totalElements}</strong>
      </span>
      <div className="flex items-center gap-2">
        <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
          Previous
        </Button>
        <span className="tabular-nums">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <Button size="sm" variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
          Next
        </Button>
      </div>
    </div>
  );
}
