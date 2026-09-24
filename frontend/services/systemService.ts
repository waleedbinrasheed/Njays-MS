import { api } from "@/services/apiClient";

export interface HealthStatus {
  status: string;
}

export const systemService = {
  health: (signal?: AbortSignal) => api.get<HealthStatus>("/health", undefined, signal),
};
