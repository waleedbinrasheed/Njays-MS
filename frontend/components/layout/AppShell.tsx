"use client";

import Link from "next/link";
import { BRAND_NAME, BrandLogo } from "@/components/brand/Brand";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { NAV_GROUPS, type NavItem } from "@/features/navigation/navItems";

/** The most specific menu entry wins, so "/inventory/movements" highlights Stock Movement, not Fabric Stock. */
function isActive(pathname: string, href: string) {
  if (href === "/") return pathname === "/";
  if (pathname !== href && !pathname.startsWith(`${href}/`)) return false;
  const more = NAV_GROUPS.flatMap((g) => g.items).some(
    (i) => i.href !== href && i.href.startsWith(`${href}/`) && (pathname === i.href || pathname.startsWith(`${i.href}/`)),
  );
  return !more;
}

function NavLink({ item, pathname }: { item: NavItem; pathname: string }) {
  const active = isActive(pathname, item.href);
  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      className={`block rounded-md px-3 py-1.5 text-sm ${
        active ? "bg-blue-50 font-medium text-blue-700" : "text-slate-700 hover:bg-slate-100 hover:text-slate-900"
      }`}
    >
      {item.label}
    </Link>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const { user, hasPermission, logout } = useAuth();
  const pathname = usePathname();
  // The mobile menu belongs to the page it was opened on, so navigating closes it.
  const [menuOpenedOn, setMenuOpenedOn] = useState<string | null>(null);
  const menuOpen = menuOpenedOn === pathname;
  const setMenuOpen = (open: boolean) => setMenuOpenedOn(open ? pathname : null);

  const groups = NAV_GROUPS.map((g) => ({ ...g, items: g.items.filter((i) => hasPermission(i.permission)) })).filter(
    (g) => g.items.length > 0,
  );

  const sidebar = (
    <nav aria-label="Main" className="space-y-5 px-3 py-4">
      {groups.map((group) => (
        <div key={group.label || "main"}>
          {group.label && (
            <p className="mb-1 px-3 text-[11px] font-semibold uppercase tracking-wider text-slate-400">{group.label}</p>
          )}
          <div className="space-y-0.5">
            {group.items.map((item) => (
              <NavLink key={item.href} item={item} pathname={pathname} />
            ))}
          </div>
        </div>
      ))}
    </nav>
  );

  return (
    <div className="flex min-h-screen flex-1 flex-col">
      <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-slate-200 bg-white px-4 print:hidden">
        <div className="flex items-center gap-3">
          <button
            type="button"
            className="rounded p-1.5 text-slate-600 hover:bg-slate-100 lg:hidden"
            onClick={() => setMenuOpen(!menuOpen)}
            aria-label="Toggle navigation"
            aria-expanded={menuOpen}
          >
            <svg viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5" aria-hidden>
              <path d="M2 4.75A.75.75 0 0 1 2.75 4h14.5a.75.75 0 0 1 0 1.5H2.75A.75.75 0 0 1 2 4.75Zm0 5A.75.75 0 0 1 2.75 9h14.5a.75.75 0 0 1 0 1.5H2.75A.75.75 0 0 1 2 9.75Zm0 5a.75.75 0 0 1 .75-.75h14.5a.75.75 0 0 1 0 1.5H2.75a.75.75 0 0 1-.75-.75Z" />
            </svg>
          </button>
          <Link href="/" className="flex items-center gap-2.5">
            <BrandLogo className="h-10 w-auto" />
            <span className="leading-tight">
              <span className="block text-sm font-semibold text-slate-900">{BRAND_NAME}</span>
              <span className="block text-xs text-slate-500">Inventory Management</span>
            </span>
          </Link>
        </div>
        <div className="flex items-center gap-3 text-sm">
          <span className="hidden text-slate-600 sm:inline">{user?.fullName}</span>
          <Link href="/change-password" className="text-slate-500 hover:text-slate-800">
            Password
          </Link>
          <button type="button" onClick={() => logout()} className="font-medium text-slate-700 hover:text-slate-900">
            Sign out
          </button>
        </div>
      </header>

      <div className="flex flex-1">
        <aside className="hidden w-60 shrink-0 border-r border-slate-200 bg-white lg:block print:hidden">{sidebar}</aside>
        {menuOpen && (
          <div className="fixed inset-0 top-14 z-20 lg:hidden">
            <div className="absolute inset-0 bg-slate-900/30" onClick={() => setMenuOpen(false)} aria-hidden />
            <aside className="relative h-full w-64 overflow-y-auto bg-white shadow-lg">{sidebar}</aside>
          </div>
        )}
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8 print:p-0">{children}</main>
      </div>
    </div>
  );
}
