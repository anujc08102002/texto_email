import { ApiClientError } from "@/lib/api";
import { getAdminToken } from "@/lib/admin-session";
import type { ApiResponse, PlatformStatus } from "@/types/api";
import type { OpsDashboard, OpsEmailDetail, OpsHealth, OpsPage, OpsQueueStatus } from "@/types/ops";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export function queryString(values: Record<string, string | number | undefined | null>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") params.set(key, String(value));
  });
  const value = params.toString();
  return value ? `?${value}` : "";
}

export async function adminGet<T>(path: string): Promise<T> {
  const token = getAdminToken();
  if (!token) throw new ApiClientError("Your admin session has expired.", 401, "ADMIN_SESSION_REQUIRED");
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      cache: "no-store",
      headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
    });
  } catch {
    throw new ApiClientError("Unable to reach the operations API.", 0);
  }
  const raw = await response.text();
  let payload: ApiResponse<T>;
  try { payload = JSON.parse(raw) as ApiResponse<T>; }
  catch { throw new ApiClientError(`Operations API returned an invalid response (${response.status}).`, response.status); }
  if (!response.ok || !payload.success || payload.data === null) {
    throw new ApiClientError(payload.error?.message ?? "Operations request failed.", response.status, payload.error?.code);
  }
  return payload.data;
}

export const getOpsPage = <T>(path: string) => adminGet<OpsPage<T>>(path);
export const getOpsDashboard = () => adminGet<OpsDashboard>("/api/v1/admin/dashboard");
export const getOpsQueues = () => adminGet<OpsQueueStatus>("/api/v1/admin/queues");
export const getOpsHealth = () => adminGet<OpsHealth>("/api/v1/admin/health");
export const getOpsEmail = (id: string) => adminGet<OpsEmailDetail>(`/api/v1/admin/emails/${encodeURIComponent(id)}`);

export async function getPublicPlatformStatus(): Promise<PlatformStatus | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/status`, { cache: "no-store", headers: { Accept: "application/json" } });
    const payload = (await response.json()) as ApiResponse<PlatformStatus>;
    return response.ok && payload.success ? payload.data : null;
  } catch { return null; }
}

