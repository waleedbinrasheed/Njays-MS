import type { PermissionCode } from "@/types/auth";

export interface NavItem {
  label: string;
  href: string;
  permission: PermissionCode;
  /** False until the phase that builds the screen; shown disabled so the menu stays stable. */
  ready: boolean;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

/** Single business, single inventory: there is intentionally no branch navigation anywhere. */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: "",
    items: [{ label: "Dashboard", href: "/", permission: "DASHBOARD_VIEW", ready: true }],
  },
  {
    label: "Inventory",
    items: [
      { label: "Inventory", href: "/inventory", permission: "INVENTORY_VIEW", ready: true },
      { label: "Stock Movement", href: "/inventory/movements", permission: "INVENTORY_VIEW", ready: true },
      { label: "Stock Adjustment", href: "/inventory/adjustments", permission: "STOCK_ADJUST", ready: true },
    ],
  },
  {
    label: "Sales",
    items: [
      { label: "New Sale", href: "/sales/new", permission: "SALE_CREATE", ready: true },
      { label: "Sales History", href: "/sales", permission: "SALE_VIEW", ready: true },
      { label: "Sales Returns", href: "/sales/returns", permission: "SALE_VIEW", ready: true },
    ],
  },
  {
    label: "Purchases",
    items: [
      { label: "New Purchase", href: "/purchases/new", permission: "PURCHASE_CREATE", ready: true },
      { label: "Purchase History", href: "/purchases", permission: "PURCHASE_VIEW", ready: true },
      { label: "Purchase Returns", href: "/purchases/returns", permission: "PURCHASE_VIEW", ready: true },
    ],
  },
  {
    label: "Master Data",
    items: [
      { label: "Books/Products", href: "/master/products", permission: "MASTER_DATA_VIEW", ready: true },
      { label: "Publishers", href: "/master/publishers", permission: "MASTER_DATA_VIEW", ready: true },
      { label: "Categories", href: "/master/categories", permission: "MASTER_DATA_VIEW", ready: true },
      { label: "Suppliers", href: "/master/suppliers", permission: "MASTER_DATA_VIEW", ready: true },
      { label: "Representatives", href: "/master/representatives", permission: "MASTER_DATA_VIEW", ready: true },
      { label: "Customers", href: "/master/customers", permission: "MASTER_DATA_VIEW", ready: true },
    ],
  },
  {
    label: "Reports",
    items: [
      { label: "Inventory Report", href: "/reports/inventory", permission: "REPORT_VIEW", ready: true },
      { label: "Sales Report", href: "/reports/sales", permission: "REPORT_VIEW", ready: true },
      { label: "Purchase Report", href: "/reports/purchases", permission: "REPORT_VIEW", ready: true },
      { label: "Stock Movement Report", href: "/reports/stock-movement", permission: "REPORT_VIEW", ready: true },
      { label: "Low Stock Report", href: "/reports/low-stock", permission: "REPORT_VIEW", ready: true },
      { label: "Representative Report", href: "/reports/representatives", permission: "REPORT_VIEW", ready: true },
    ],
  },
  {
    label: "Administration",
    items: [
      { label: "Users", href: "/admin/users", permission: "USER_MANAGE", ready: true },
      { label: "Roles", href: "/admin/roles", permission: "ROLE_MANAGE", ready: true },
    ],
  },
];
