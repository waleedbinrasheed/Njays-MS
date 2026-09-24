export interface RoleRef {
  id: number;
  name: string;
}

export interface User {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  mobile: string | null;
  active: boolean;
  mustChangePassword: boolean;
  roles: RoleRef[];
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserInput {
  username: string;
  fullName: string;
  email: string;
  mobile: string;
  password: string;
  roleIds: number[];
  active: boolean;
}

export interface UpdateUserInput {
  fullName: string;
  email: string;
  mobile: string;
  roleIds: number[];
  active: boolean;
}

export interface Permission {
  code: string;
  module: string;
  description: string;
}

export interface Role {
  id: number;
  name: string;
  description: string | null;
  systemRole: boolean;
  permissions: string[];
  userCount: number;
  updatedAt: string;
}

export interface RoleInput {
  name: string;
  description: string;
  permissions: string[];
}
