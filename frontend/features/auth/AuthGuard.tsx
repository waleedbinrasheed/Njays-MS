"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { useAuth } from "@/features/auth/AuthProvider";
import type { PermissionCode } from "@/types/auth";
import { loginUrl } from "@/utils/navigation";

/**
 * Keeps signed-out users (and users with a pending password change) out of the app screens.
 * This is only for navigation: every API call is authorized again by the backend.
 */
export function AuthGuard({ children }: { children: ReactNode }) {
  const { status, user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") router.replace(loginUrl({ next: pathname }));
    else if (status === "authenticated" && user?.mustChangePassword) router.replace("/change-password");
  }, [status, user, router, pathname]);

  if (status !== "authenticated" || !user || user.mustChangePassword) return <PageSpinner />;
  return <>{children}</>;
}

/** Shows a friendly message instead of a screen the user is not allowed to use. */
export function RequirePermission({ permission, children }: { permission: PermissionCode; children: ReactNode }) {
  const { hasPermission } = useAuth();
  if (!hasPermission(permission)) {
    return (
      <EmptyState
        title="You do not have access to this page"
        description="Ask an administrator if you need this permission."
      />
    );
  }
  return <>{children}</>;
}
