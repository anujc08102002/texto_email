import { apiGet, apiPost, apiPostEmpty } from "@/lib/api";
import type { AuthSession, AuthUser } from "@/types/api";

export function registerAccount(payload: { organization: string; email: string; password: string }) {
  return apiPost<AuthSession>("/api/v1/auth/register", payload);
}

export function loginAccount(payload: { email: string; password: string }) {
  return apiPost<AuthSession>("/api/v1/auth/login", payload);
}

export function fetchCurrentUser() {
  return apiGet<AuthUser>("/api/v1/auth/me");
}

export function logoutAccount() {
  return apiPostEmpty("/api/v1/auth/logout");
}
