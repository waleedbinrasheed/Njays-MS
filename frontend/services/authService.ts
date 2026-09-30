import { apiRequest } from "@/services/apiClient";
import type { CurrentUser } from "@/types/auth";

export const authService = {
  login: (username: string, password: string) =>
    apiRequest<CurrentUser>("/auth/login", {
      method: "POST",
      body: { username, password },
      skipAuthRedirect: true,
      // Safe to repeat: a 502/503/504 means the sleeping server has not received it yet.
      retryWhileStarting: true,
    }),

  logout: () => apiRequest<void>("/auth/logout", { method: "POST", skipAuthRedirect: true }),

  me: () => apiRequest<CurrentUser>("/auth/me", { skipAuthRedirect: true }),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>("/auth/change-password", {
      method: "POST",
      body: { currentPassword, newPassword },
    }),
};
