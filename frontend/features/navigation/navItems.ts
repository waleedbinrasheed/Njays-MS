import type { PermissionCode } from "@/types/auth";

export interface NavItem {
  label: string;
  href: string;
  permission: PermissionCode;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

/** Single shop, single fabric stock: there is intentionally no branch navigation anywhere. */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: "",
    items: [
      { label: "Dashboard", href: "/", permission: "DASHBOARD_VIEW" },
      { label: "Orders", href: "/orders", permission: "ORDER_VIEW" },
      { label: "Calendar", href: "/calendar", permission: "ORDER_VIEW" },
    ],
  },
  {
    label: "Master Data",
    items: [
      { label: "Customers", href: "/master/customers", permission: "MASTER_DATA_VIEW" },
      { label: "Fabrics", href: "/master/fabrics", permission: "MASTER_DATA_VIEW" },
      { label: "Dress Types", href: "/master/garment-types", permission: "MASTER_DATA_VIEW" },
      { label: "Measurements", href: "/master/measurements", permission: "MASTER_DATA_VIEW" },
    ],
  },
  {
    label: "Inventory",
    items: [
      { label: "Fabric Stock", href: "/inventory", permission: "INVENTORY_VIEW" },
      { label: "Stock Movement", href: "/inventory/movements", permission: "INVENTORY_VIEW" },
      { label: "Stock Adjustment", href: "/inventory/adjustments", permission: "STOCK_ADJUST" },
    ],
  },
  {
    label: "Reports",
    items: [
      { label: "Orders Report", href: "/reports/orders", permission: "REPORT_VIEW" },
      { label: "Fabric Stock Report", href: "/reports/fabric-stock", permission: "REPORT_VIEW" },
    ],
  },
  {
    label: "Administration",
    items: [
      { label: "Users", href: "/admin/users", permission: "USER_MANAGE" },
      { label: "Roles", href: "/admin/roles", permission: "ROLE_MANAGE" },
    ],
  },
];
