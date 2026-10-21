import { getStoredUser } from "@/lib/session";

function key(pageName: string) {
  const user = getStoredUser();
  return `texto.columns:${user?.email ?? "anonymous"}:${pageName}`;
}
export async function getColSettings(pageName: string): Promise<Record<string, boolean> | null> {
  try { const saved = JSON.parse(localStorage.getItem(key(pageName)) ?? "null");
    return saved && !Array.isArray(saved) && typeof saved === "object" && Object.values(saved).every((value) => typeof value === "boolean") ? saved : null;
  } catch { return null; }
}
export async function saveColSettings(pageName: string, visibility: Record<string, boolean>): Promise<boolean> {
  try { localStorage.setItem(key(pageName), JSON.stringify(visibility)); return true; } catch { return false; }
}
