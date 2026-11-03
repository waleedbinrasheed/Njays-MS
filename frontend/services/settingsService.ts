import { api } from "@/services/apiClient";
import type { BusinessDetails } from "@/types/settings";

export const settingsService = {
  business: (signal?: AbortSignal) => api.get<BusinessDetails>("/settings/business", undefined, signal),
};
