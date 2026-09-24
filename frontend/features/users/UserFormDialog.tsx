"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Checkbox, TextField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { ApiError } from "@/services/apiClient";
import { userService } from "@/services/userService";
import type { Role, User } from "@/types/user";

/** Mount only while open, with key={user?.id ?? "new"}, so the form starts fresh each time. */
interface UserFormDialogProps {
  user: User | null; // null = create
  roles: Role[];
  onClose: () => void;
  onSaved: (user: User, created: boolean) => void;
}

interface FormState {
  username: string;
  fullName: string;
  email: string;
  mobile: string;
  password: string;
  roleIds: number[];
  active: boolean;
}

function initialState(user: User | null, roles: Role[]): FormState {
  if (user) {
    return {
      username: user.username,
      fullName: user.fullName,
      email: user.email ?? "",
      mobile: user.mobile ?? "",
      password: "",
      roleIds: user.roles.map((r) => r.id),
      active: user.active,
    };
  }
  const normal = roles.find((r) => r.name === "NORMAL_USER");
  return { username: "", fullName: "", email: "", mobile: "", password: "", roleIds: normal ? [normal.id] : [], active: true };
}

export function UserFormDialog({ user, roles, onClose, onSaved }: UserFormDialogProps) {
  const isEdit = user !== null;
  const [form, setForm] = useState<FormState>(() => initialState(user, roles));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }));

  const toggleRole = (id: number) =>
    set("roleIds", form.roleIds.includes(id) ? form.roleIds.filter((r) => r !== id) : [...form.roleIds, id]);

  function validate(): boolean {
    const local: Record<string, string> = {};
    if (!isEdit && !form.username.trim()) local.username = "Username is required";
    if (!form.fullName.trim()) local.fullName = "Full name is required";
    if (!isEdit && !form.password) local.password = "Temporary password is required";
    if (form.roleIds.length === 0) local.roleIds = "Select at least one role";
    setErrors(local);
    return Object.keys(local).length === 0;
  }

  async function save() {
    setSaving(true);
    setFormError(null);
    try {
      const saved = isEdit
        ? await userService.update(user.id, {
            fullName: form.fullName,
            email: form.email,
            mobile: form.mobile,
            roleIds: form.roleIds,
            active: form.active,
          })
        : await userService.create(form);
      onSaved(saved, !isEdit);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        if (!Object.keys(err.fieldErrors).length) setFormError(err.message);
      } else {
        setFormError("Could not save the user. Please try again.");
      }
    } finally {
      setSaving(false);
      setConfirmDeactivate(false);
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    if (isEdit && user.active && !form.active) {
      setConfirmDeactivate(true);
      return;
    }
    void save();
  }

  return (
    <>
      <Modal
        open={!confirmDeactivate}
        title={isEdit ? `Edit user: ${user.username}` : "New user"}
        onClose={onClose}
        footer={
          <>
            <Button variant="secondary" onClick={onClose} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="user-form" loading={saving}>
              {isEdit ? "Save changes" : "Create user"}
            </Button>
          </>
        }
      >
        <form id="user-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError && (
            <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {formError}
            </p>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="Username"
              value={form.username}
              onChange={(e) => set("username", e.target.value)}
              error={errors.username}
              disabled={isEdit}
              hint={isEdit ? "Usernames cannot be changed" : "Letters, numbers, dot, dash or underscore"}
              autoComplete="off"
              required
            />
            <TextField
              label="Full name"
              value={form.fullName}
              onChange={(e) => set("fullName", e.target.value)}
              error={errors.fullName}
              required
            />
            <TextField
              label="Email"
              type="email"
              value={form.email}
              onChange={(e) => set("email", e.target.value)}
              error={errors.email}
            />
            <TextField
              label="Mobile"
              type="tel"
              value={form.mobile}
              onChange={(e) => set("mobile", e.target.value)}
              error={errors.mobile}
              placeholder="0300-1234567"
            />
          </div>
          {!isEdit && (
            <TextField
              label="Temporary password"
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => set("password", e.target.value)}
              error={errors.password}
              hint="At least 8 characters with letters and numbers. The user must change it at first sign-in."
              required
            />
          )}
          <fieldset>
            <legend className="mb-2 text-sm font-medium text-slate-700">
              Roles<span className="ml-0.5 text-red-500">*</span>
            </legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {roles.map((role) => (
                <Checkbox
                  key={role.id}
                  label={role.name}
                  description={role.description ?? undefined}
                  checked={form.roleIds.includes(role.id)}
                  onChange={() => toggleRole(role.id)}
                />
              ))}
            </div>
            {errors.roleIds && <p className="mt-1 text-xs text-red-600">{errors.roleIds}</p>}
          </fieldset>
          <Checkbox
            label="Active"
            description="Inactive users cannot sign in."
            checked={form.active}
            onChange={(e) => set("active", e.target.checked)}
          />
        </form>
      </Modal>
      <ConfirmDialog
        open={confirmDeactivate}
        title="Deactivate user?"
        message={
          <>
            <strong>{user?.username}</strong> will be signed out immediately and will not be able to sign in again until
            reactivated.
          </>
        }
        confirmLabel="Deactivate"
        danger
        loading={saving}
        onConfirm={() => void save()}
        onCancel={() => setConfirmDeactivate(false)}
      />
    </>
  );
}
