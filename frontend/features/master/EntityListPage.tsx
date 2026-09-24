"use client";

import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Badge, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { SortableHeader, nextSort, toSortParam, type SortState } from "@/components/ui/SortableHeader";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { ApiError } from "@/services/apiClient";
import type { ListQuery } from "@/services/masterDataService";
import type { Page } from "@/types/api";

export interface Column<T> {
  header: string;
  sortField?: string;
  render: (row: T) => ReactNode;
  className?: string;
}

export interface FormRenderProps<T> {
  record: T | null;
  onClose: () => void;
  onSaved: (record: T, created: boolean) => void;
}

interface EntityListPageProps<T extends { id: number; active: boolean }> {
  title: string;
  description: string;
  /** Singular, lower case: "publisher". */
  noun: string;
  list: (query: ListQuery, signal: AbortSignal) => Promise<Page<T>>;
  remove: (id: number) => Promise<void>;
  columns: Column<T>[];
  defaultSort: SortState;
  searchPlaceholder: string;
  displayName: (row: T) => string;
  renderForm: (props: FormRenderProps<T>) => ReactNode;
  /** Rows that may never be deleted (e.g. the walk-in customer). */
  canDelete?: (row: T) => boolean;
  /** Extra filter controls and the query parameters they produce. */
  filters?: ReactNode;
  filterQuery?: Record<string, string | number | undefined>;
}

const PAGE_SIZE = 20;
type StatusFilter = "all" | "active" | "inactive";

/**
 * Standard master-data screen: search, status filter, sortable paged table, create/edit dialog and
 * delete confirmation. Users without MASTER_DATA_MANAGE get a read-only view (the API enforces it too).
 */
export function EntityListPage<T extends { id: number; active: boolean }>({
  title,
  description,
  noun,
  list,
  remove,
  columns,
  defaultSort,
  searchPlaceholder,
  displayName,
  renderForm,
  canDelete = () => true,
  filters,
  filterQuery = {},
}: EntityListPageProps<T>) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canManage = hasPermission("MASTER_DATA_MANAGE");

  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>("active");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>(defaultSort);
  const [editing, setEditing] = useState<T | null | undefined>(undefined);
  const [deleting, setDeleting] = useState<T | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const debouncedSearch = useDebouncedValue(search);

  // Filters from the parent reset paging when they change.
  const filterKey = JSON.stringify(filterQuery);
  const [seenFilterKey, setSeenFilterKey] = useState(filterKey);
  if (seenFilterKey !== filterKey) {
    setSeenFilterKey(filterKey);
    setPage(0);
  }

  const query = useApiQuery(
    (signal) =>
      list(
        {
          search: debouncedSearch,
          active: status === "all" ? undefined : status === "active",
          page,
          size: PAGE_SIZE,
          sort: toSortParam(sort),
          ...filterQuery,
        },
        signal,
      ),
    [debouncedSearch, status, page, sort, filterKey],
  );

  async function confirmDelete() {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await remove(deleting.id);
      toast.success(`${capitalize(noun)} "${displayName(deleting)}" deleted`);
      query.reload();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : `Could not delete the ${noun}`);
    } finally {
      setDeleteBusy(false);
      setDeleting(null);
    }
  }

  const rows = query.data?.content ?? [];
  const onSort = (field: string) => {
    setSort((s) => nextSort(s, field));
    setPage(0);
  };

  return (
    <>
      <PageHeader
        title={title}
        description={description}
        actions={canManage ? <Button onClick={() => setEditing(null)}>New {noun}</Button> : undefined}
      />

      <div className="mb-4 flex flex-col flex-wrap gap-2 sm:flex-row sm:items-center">
        <SearchInput
          placeholder={searchPlaceholder}
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label={`Search ${title.toLowerCase()}`}
        />
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as StatusFilter);
            setPage(0);
          }}
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
          aria-label="Filter by status"
        >
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
          <option value="all">All</option>
        </select>
        {filters}
        {query.loading && query.data && <Spinner size="sm" />}
      </div>

      {query.error && (
        <div className="mb-4">
          <ErrorBanner message={query.error} onRetry={query.reload} />
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                {columns.map((c) =>
                  c.sortField ? (
                    <SortableHeader key={c.header} label={c.header} field={c.sortField} sort={sort} onSort={onSort} />
                  ) : (
                    <th key={c.header} scope="col" className={`px-4 py-2.5 font-medium ${c.className ?? ""}`}>
                      {c.header}
                    </th>
                  ),
                )}
                <th scope="col" className="px-4 py-2.5">
                  <span className="sr-only">Status and actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((row) => (
                <tr key={row.id} className="hover:bg-slate-50">
                  {columns.map((c) => (
                    <td key={c.header} className={`px-4 py-2.5 ${c.className ?? ""}`}>
                      {c.render(row)}
                    </td>
                  ))}
                  <td className="whitespace-nowrap px-4 py-2.5 text-right">
                    {!row.active && (
                      <span className="mr-2">
                        <Badge tone="red">Inactive</Badge>
                      </span>
                    )}
                    {canManage && (
                      <>
                        <Button size="sm" variant="ghost" onClick={() => setEditing(row)}>
                          Edit
                        </Button>
                        {canDelete(row) && (
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-red-600 hover:text-red-700"
                            onClick={() => setDeleting(row)}
                          >
                            Delete
                          </Button>
                        )}
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {query.loading && !query.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label={`Loading ${title.toLowerCase()}...`} />
          </div>
        )}
        {!query.loading && query.data && rows.length === 0 && (
          <EmptyState
            title={`No ${title.toLowerCase()} found`}
            description={search ? "Try a different search or filter." : undefined}
            action={
              canManage && !search ? <Button onClick={() => setEditing(null)}>Add the first {noun}</Button> : undefined
            }
          />
        )}
        {query.data && (
          <Pagination
            page={query.data.page}
            size={query.data.size}
            totalElements={query.data.totalElements}
            totalPages={query.data.totalPages}
            onPageChange={setPage}
          />
        )}
      </div>

      {editing !== undefined && (
        <div key={editing?.id ?? "new"}>
          {renderForm({
            record: editing,
            onClose: () => setEditing(undefined),
            onSaved: (saved, created) => {
              setEditing(undefined);
              toast.success(`${capitalize(noun)} "${displayName(saved)}" ${created ? "created" : "saved"}`);
              query.reload();
            },
          })}
        </div>
      )}
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${noun}?`}
        message={
          <>
            Delete <strong>{deleting ? displayName(deleting) : ""}</strong>? This cannot be undone. Records that are
            already in use cannot be deleted; deactivate them instead.
          </>
        }
        confirmLabel="Delete"
        danger
        loading={deleteBusy}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleting(null)}
      />
    </>
  );
}

function capitalize(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
