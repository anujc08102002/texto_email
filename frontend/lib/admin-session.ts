export type AdminUser = {
  email: string;
  name: string;
  role: "PLATFORM_ADMIN";
};

export type AdminSession = {
  token: string;
  admin: AdminUser;
};

const TOKEN_KEY = "texto.admin.token";
const USER_KEY = "texto.admin.user";
export const ADMIN_SESSION_EVENT = "texto-admin-session-change";

function notify() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(ADMIN_SESSION_EVENT));
  }
}

export function saveAdminSession(session: AdminSession) {
  localStorage.setItem(TOKEN_KEY, session.token);
  localStorage.setItem(USER_KEY, JSON.stringify(session.admin));
  notify();
}

export function getAdminToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredAdmin(): AdminUser | null {
  if (typeof window === "undefined") return null;
  const raw = getStoredAdminSnapshot();
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AdminUser;
  } catch {
    return null;
  }
}

export function getStoredAdminSnapshot(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(USER_KEY);
}

export function subscribeAdminSession(callback: () => void) {
  window.addEventListener(ADMIN_SESSION_EVENT, callback);
  window.addEventListener("storage", callback);
  return () => {
    window.removeEventListener(ADMIN_SESSION_EVENT, callback);
    window.removeEventListener("storage", callback);
  };
}

export function clearAdminSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  notify();
}
