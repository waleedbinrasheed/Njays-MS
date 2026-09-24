import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type { CreateUserInput, Permission, Role, RoleInput, UpdateUserInput, User } from "@/types/user";

export interface UserSearch extends PageQuery {
  search?: string;
  active?: boolean;
}

export const userService = {
  list: (query: UserSearch, signal?: AbortSignal) =>
    api.get<Page<User>>("/users", { ...query }, signal),
  get: (id: number) => api.get<User>(`/users/${id}`),
  create: (input: CreateUserInput) => api.post<User>("/users", input),
  update: (id: number, input: UpdateUserInput) => api.put<User>(`/users/${id}`, input),
  resetPassword: (id: number, newPassword: string) =>
    api.post<void>(`/users/${id}/reset-password`, { newPassword }),
};

export const roleService = {
  list: (signal?: AbortSignal) => api.get<Role[]>("/roles", undefined, signal),
  permissions: (signal?: AbortSignal) => api.get<Permission[]>("/permissions", undefined, signal),
  create: (input: RoleInput) => api.post<Role>("/roles", input),
  update: (id: number, input: RoleInput) => api.put<Role>(`/roles/${id}`, input),
  delete: (id: number) => api.delete<void>(`/roles/${id}`),
};
