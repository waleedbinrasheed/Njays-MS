"use client";

import { useMemo, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, TextField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { ApiError } from "@/services/apiClient";
import { roleService } from "@/services/userService";
import type { Permission, Role } from "@/types/user";

/** Mount only while open, with key={role?.id ?? "new"}. */
interface RoleFormDialogProps {
  role: Role | null; // null = create
  permissions: Permission[];
  onClose: () => void;
  onSaved: (role: Role, created: boolean) => void;
}

export function RoleFormDialog({ role, permissions, onClose, onSaved }: RoleFormDialogProps) {
  const isEdit = role !== null;
  const isAdminRole = role?.name === "ADMIN";
  const [name, setName] = useState(role?.name ?? "");
  const [description, setDescription] = useState(role?.description ?? "");
  const [selected, setSelected] = useState<Set<string>>(() => new Set(role?.permissions ?? []));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const byModule = useMemo(() => {
    const groups = new Map<string, Permission[]>();
    for (const p of permissions) groups.set(p.module, [...(groups.get(p.module) ?? []), p]);
    return [...groups.entries()];
  }, [permissions]);

  const toggle = (code: string) =>
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setErrors({ name: "Role name is required" });
      return;
    }
    setSaving(true);
    setFormError(null);
    const input = { name, description, permissions: [...selected] };
    try {
      const saved = isEdit ? await roleService.update(role.id, input) : await roleService.create(input);
      onSaved(saved, !isEdit);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        if (!Object.keys(err.fieldErrors).length) setFormError(err.message);
      } else {
        setFormError("Could not save the role. Please try again.");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      size="lg"
      title={isEdit ? `Edit role: ${role.name}` : "New role"}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="role-form" loading={saving}>
            {isEdit ? "Save changes" : "Create role"}
          </Button>
        </>
      }
    >
      <form id="role-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
        {formError && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError}
          </p>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={errors.name}
            disabled={role?.systemRole}
            hint={role?.systemRole ? "Built-in roles cannot be renamed" : "Saved in capitals, e.g. STORE_MANAGER"}
            required
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            error={errors.description}
          />
        </div>
        <fieldset>
          <legend className="mb-2 text-sm font-medium text-slate-700">Permissions</legend>
          {isAdminRole && (
            <p className="mb-3 rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-800">
              The ADMIN role always has every permission.
            </p>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            {byModule.map(([module, perms]) => (
              <div key={module} className="rounded-md border border-slate-200 p-3">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">{module}</p>
                <div className="space-y-2">
                  {perms.map((p) => (
                    <Checkbox
                      key={p.code}
                      label={p.description}
                      checked={isAdminRole || selected.has(p.code)}
                      disabled={isAdminRole}
                      onChange={() => toggle(p.code)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </fieldset>
      </form>
    </Modal>
  );
}
