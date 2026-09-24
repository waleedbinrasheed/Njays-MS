"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Badge, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { SortableHeader, nextSort, toSortParam, type SortState } from "@/components/ui/SortableHeader";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { roleService, userService } from "@/services/userService";
import type { User } from "@/types/user";
import { formatDateTime } from "@/utils/format";
import { ResetPasswordDialog } from "./ResetPasswordDialog";
import { UserFormDialog } from "./UserFormDialog";

const PAGE_SIZE = 20;
type StatusFilter = "all" | "active" | "inactive";

export function UsersPage() {
  const toast = useToast();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: "username", direction: "asc" });
  const [editing, setEditing] = useState<User | null | undefined>(undefined); // undefined = closed, null = new
  const [resetting, setResetting] = useState<User | null>(null);
  const debouncedSearch = useDebouncedValue(search);

  const users = useApiQuery(
    (signal) =>
      userService.list(
        {
          search: debouncedSearch,
          active: status === "all" ? undefined : status === "active",
          page,
          size: PAGE_SIZE,
          sort: toSortParam(sort),
        },
        signal,
      ),
    [debouncedSearch, status, page, sort],
  );
  const roles = useApiQuery((signal) => roleService.list(signal), []);

  const onSort = (field: string) => {
    setSort((s) => nextSort(s, field));
    setPage(0);
  };

  const rows = users.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Users"
        description="People who can sign in to the system."
        actions={
          <Button onClick={() => setEditing(null)} disabled={!roles.data}>
            New user
          </Button>
        }
      />

      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <SearchInput
          placeholder="Search name, username or email"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label="Search users"
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
          <option value="all">All statuses</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </select>
        {users.loading && users.data && <Spinner size="sm" />}
      </div>

      {users.error && <ErrorBanner message={users.error} onRetry={users.reload} />}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <SortableHeader label="Username" field="username" sort={sort} onSort={onSort} />
                <SortableHeader label="Full name" field="fullName" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 font-medium">Roles</th>
                <SortableHeader label="Status" field="active" sort={sort} onSort={onSort} />
                <SortableHeader label="Last sign-in" field="lastLoginAt" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 text-right font-medium">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((u) => (
                <tr key={u.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-2.5 font-medium text-slate-900">{u.username}</td>
                  <td className="px-4 py-2.5">
                    <div className="text-slate-800">{u.fullName}</div>
                    {u.email && <div className="text-xs text-slate-500">{u.email}</div>}
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex flex-wrap gap-1">
                      {u.roles.map((r) => (
                        <Badge key={r.id} tone={r.name === "ADMIN" ? "blue" : "gray"}>
                          {r.name}
                        </Badge>
                      ))}
                    </div>
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5">
                    {u.active ? <Badge tone="green">Active</Badge> : <Badge tone="red">Inactive</Badge>}
                    {u.mustChangePassword && (
                      <span className="ml-1">
                        <Badge tone="amber">Temp password</Badge>
                      </span>
                    )}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-slate-600">{formatDateTime(u.lastLoginAt)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right">
                    <Button size="sm" variant="ghost" onClick={() => setEditing(u)} disabled={!roles.data}>
                      Edit
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setResetting(u)}>
                      Reset password
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {users.loading && !users.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading users..." />
          </div>
        )}
        {!users.loading && users.data && rows.length === 0 && (
          <EmptyState title="No users found" description={search ? "Try a different search." : undefined} />
        )}
        {users.data && (
          <Pagination
            page={users.data.page}
            size={users.data.size}
            totalElements={users.data.totalElements}
            totalPages={users.data.totalPages}
            onPageChange={setPage}
          />
        )}
      </div>

      {roles.data && editing !== undefined && (
        <UserFormDialog
          key={editing?.id ?? "new"}
          user={editing}
          roles={roles.data}
          onClose={() => setEditing(undefined)}
          onSaved={(saved, created) => {
            setEditing(undefined);
            toast.success(created ? `User ${saved.username} created` : `User ${saved.username} updated`);
            users.reload();
          }}
        />
      )}
      {resetting && (
        <ResetPasswordDialog
          key={resetting.id}
          user={resetting}
          onClose={() => setResetting(null)}
          onDone={(u) => {
            setResetting(null);
            toast.success(`Password reset for ${u.username}. They must change it at next sign-in.`);
            users.reload();
          }}
        />
      )}
    </>
  );
}
