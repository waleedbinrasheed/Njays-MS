"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Badge, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useApiQuery } from "@/hooks/useApiQuery";
import { ApiError } from "@/services/apiClient";
import { roleService } from "@/services/userService";
import type { Role } from "@/types/user";
import { RoleFormDialog } from "./RoleFormDialog";

export function RolesPage() {
  const toast = useToast();
  const roles = useApiQuery((signal) => roleService.list(signal), []);
  const permissions = useApiQuery((signal) => roleService.permissions(signal), []);
  const [editing, setEditing] = useState<Role | null | undefined>(undefined);
  const [deleting, setDeleting] = useState<Role | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  async function confirmDelete() {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await roleService.delete(deleting.id);
      toast.success(`Role ${deleting.name} deleted`);
      roles.reload();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Could not delete the role");
    } finally {
      setDeleteBusy(false);
      setDeleting(null);
    }
  }

  const ready = !!permissions.data;

  return (
    <>
      <PageHeader
        title="Roles"
        description="Roles group permissions. Assign roles to users on the Users page."
        actions={
          <Button onClick={() => setEditing(null)} disabled={!ready}>
            New role
          </Button>
        }
      />

      {(roles.error || permissions.error) && (
        <ErrorBanner message={roles.error ?? permissions.error ?? ""} onRetry={roles.reload} />
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-4 py-2.5 font-medium">Role</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Permissions</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Users</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {(roles.data ?? []).map((r) => (
                <tr key={r.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2.5">
                    <div className="flex items-center gap-2 font-medium text-slate-900">
                      {r.name}
                      {r.systemRole && <Badge>Built-in</Badge>}
                    </div>
                    {r.description && <div className="text-xs text-slate-500">{r.description}</div>}
                  </td>
                  <td className="px-4 py-2.5 text-slate-700">
                    {r.name === "ADMIN" ? "All" : `${r.permissions.length} of ${permissions.data?.length ?? "–"}`}
                  </td>
                  <td className="px-4 py-2.5 tabular-nums text-slate-700">{r.userCount}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right">
                    <Button size="sm" variant="ghost" onClick={() => setEditing(r)} disabled={!ready}>
                      Edit
                    </Button>
                    {!r.systemRole && (
                      <Button size="sm" variant="ghost" className="text-red-600 hover:text-red-700" onClick={() => setDeleting(r)}>
                        Delete
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {roles.loading && !roles.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading roles..." />
          </div>
        )}
        {roles.data && roles.data.length === 0 && <EmptyState title="No roles yet" />}
      </div>

      {permissions.data && editing !== undefined && (
        <RoleFormDialog
          key={editing?.id ?? "new"}
          role={editing}
          permissions={permissions.data}
          onClose={() => setEditing(undefined)}
          onSaved={(saved, created) => {
            setEditing(undefined);
            toast.success(created ? `Role ${saved.name} created` : `Role ${saved.name} updated`);
            roles.reload();
          }}
        />
      )}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete role?"
        message={
          deleting && deleting.userCount > 0 ? (
            <>
              <strong>{deleting.name}</strong> is assigned to {deleting.userCount} user(s). Remove it from those users first.
            </>
          ) : (
            <>
              Delete the <strong>{deleting?.name}</strong> role? This cannot be undone.
            </>
          )
        }
        confirmLabel="Delete role"
        danger
        loading={deleteBusy}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleting(null)}
      />
    </>
  );
}
