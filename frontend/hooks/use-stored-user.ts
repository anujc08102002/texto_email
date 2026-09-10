"use client";

import { useMemo, useSyncExternalStore } from "react";
import { getStoredUserSnapshot, subscribeSession, type AuthUser } from "@/lib/session";

export function useStoredUser(): AuthUser | null {
  const raw = useSyncExternalStore(subscribeSession, getStoredUserSnapshot, () => null);
  return useMemo(() => {
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      return null;
    }
  }, [raw]);
}
