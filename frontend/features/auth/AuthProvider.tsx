"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { setActivityListener, setUnauthorizedListener } from "@/services/apiClient";
import { authService } from "@/services/authService";
import type { CurrentUser, PermissionCode } from "@/types/auth";
import { loginUrl } from "@/utils/navigation";

type AuthStatus = "loading" | "authenticated" | "anonymous";

interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  login: (username: string, password: string) => Promise<CurrentUser>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  hasPermission: (code: PermissionCode) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const IDLE_CHECK_INTERVAL_MS = 15_000;

/**
 * Holds the signed-in user. The backend session is the source of truth; this only mirrors it.
 * - Any 401 from the API (expired session) sends the user to the login page.
 * - The server renews its idle timer on each API call, so the client tracks the same clock and
 *   redirects as soon as the session must have expired, even if the user is not clicking.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<CurrentUser | null>(null);
  const lastActivity = useRef(0);
  const pathnameRef = useRef(pathname);

  useEffect(() => {
    pathnameRef.current = pathname;
  }, [pathname]);

  const expire = useCallback(() => {
    setUser(null);
    setStatus("anonymous");
    router.replace(loginUrl({ expired: true, next: pathnameRef.current }));
  }, [router]);

  useEffect(() => {
    authService
      .me()
      .then((me) => {
        lastActivity.current = Date.now();
        setUser(me);
        setStatus("authenticated");
      })
      .catch(() => setStatus("anonymous"));
  }, []);

  useEffect(() => {
    setActivityListener(() => {
      lastActivity.current = Date.now();
    });
    return () => setActivityListener(null);
  }, []);

  useEffect(() => {
    if (status !== "authenticated") {
      setUnauthorizedListener(null);
      return;
    }
    setUnauthorizedListener(expire);
    return () => setUnauthorizedListener(null);
  }, [status, expire]);

  useEffect(() => {
    if (status !== "authenticated" || !user) return;
    const timeoutMs = user.sessionTimeoutSeconds * 1000;
    const timer = setInterval(() => {
      if (Date.now() - lastActivity.current > timeoutMs) expire();
    }, IDLE_CHECK_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [status, user, expire]);

  const login = useCallback(async (username: string, password: string) => {
    const me = await authService.login(username, password);
    lastActivity.current = Date.now();
    setUser(me);
    setStatus("authenticated");
    return me;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
      setStatus("anonymous");
      router.replace("/login");
    }
  }, [router]);

  const refresh = useCallback(async () => {
    const me = await authService.me();
    setUser(me);
  }, []);

  const hasPermission = useCallback((code: PermissionCode) => !!user?.permissions.includes(code), [user]);

  const value = useMemo(
    () => ({ status, user, login, logout, refresh, hasPermission }),
    [status, user, login, logout, refresh, hasPermission],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
