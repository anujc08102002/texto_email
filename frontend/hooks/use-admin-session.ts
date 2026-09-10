"use client";

import { useMemo, useSyncExternalStore } from "react";
import { getStoredAdminSnapshot, subscribeAdminSession, type AdminUser } from "@/lib/admin-session";

export function useAdminSession(): AdminUser | null {
  const raw = useSyncExternalStore(subscribeAdminSession, getStoredAdminSnapshot, () => null);
  return useMemo(() => {
    if (!raw) return null;
    try {
      return JSON.parse(raw) as AdminUser;
    } catch {
      return null;
    }
  }, [raw]);
}
