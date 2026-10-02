export interface CurrentUser {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
  sessionTimeoutSeconds: number;
}

/** Permission codes enforced by the backend. */
export type PermissionCode =
  | "DASHBOARD_VIEW"
  | "USER_MANAGE"
  | "ROLE_MANAGE"
  | "SETTINGS_MANAGE"
  | "MASTER_DATA_VIEW"
  | "MASTER_DATA_MANAGE"
  | "INVENTORY_VIEW"
  | "STOCK_ADJUST"
  | "PURCHASE_VIEW"
  | "PURCHASE_CREATE"
  | "PURCHASE_CANCEL"
  | "SALE_VIEW"
  | "SALE_CREATE"
  | "SALE_CANCEL"
  | "RETURN_CREATE"
  | "REPORT_VIEW"
  | "SAMPLE_VIEW"
  | "SAMPLE_MANAGE";
