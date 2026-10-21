import { ApiClientError } from "@/lib/api";
import { getAdminToken } from "@/lib/admin-session";
import type { ApiResponse, PlatformStatus } from "@/types/api";
import type { OpsApiKey, OpsDashboard, OpsDomain, OpsEmail, OpsEmailDetail, OpsHealth, OpsPage, OpsQueueStatus, OpsTenant, OpsTenantUsage, OpsUser, OpsWebhook } from "@/types/ops";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export function queryString(values: Record<string, string | number | undefined | null>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") params.set(key, String(value));
  });
  const value = params.toString();
  return value ? `?${value}` : "";
}

async function adminRequest<T>(path: string, body?: unknown): Promise<T> {
  const token = getAdminToken();
  if (!token) throw new ApiClientError("Your admin session has expired.", 401, "ADMIN_SESSION_REQUIRED");
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: body === undefined ? "GET" : "POST",
      cache: "no-store",
      headers: { Accept: "application/json", "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: body === undefined ? undefined : JSON.stringify(body),
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

export const adminGet = <T>(path: string) => adminRequest<T>(path);

export const getOpsPage = <T>(path: string) => adminGet<OpsPage<T>>(path);
export const getOpsDashboard = () => adminGet<OpsDashboard>("/api/v1/admin/dashboard");
export const getOpsQueues = () => adminGet<OpsQueueStatus>("/api/v1/admin/queues");
export const getOpsHealth = () => adminGet<OpsHealth>("/api/v1/admin/health");
export const getOpsEmail = (id: string) => adminGet<OpsEmailDetail>(`/api/v1/admin/emails/${encodeURIComponent(id)}`);
export const getOpsTenant = (id: string) => adminGet<OpsTenant>(`/api/v1/admin/tenants/${encodeURIComponent(id)}`);
export const getOpsTenantUsage = (id: string) => adminGet<OpsTenantUsage>(`/api/v1/admin/tenants/${encodeURIComponent(id)}/usage`);
export const getOpsTenantEmails = (id: string) => getOpsPage<OpsEmail>(`/api/v1/admin/emails${queryString({ tenantId: id, page: 0, size: 10 })}`);
export const getOpsTenantUsers = (id: string) => getOpsPage<OpsUser>(`/api/v1/admin/users${queryString({ tenantId: id, page: 0, size: 20 })}`);
export const getOpsTenantDomains = (id: string) => getOpsPage<OpsDomain>(`/api/v1/admin/domains${queryString({ tenantId: id, page: 0, size: 10 })}`);
export const getOpsTenantApiKeys = (id: string) => getOpsPage<OpsApiKey>(`/api/v1/admin/api-keys${queryString({ tenantId: id, page: 0, size: 10 })}`);
export const getOpsTenantWebhooks = (id: string) => getOpsPage<OpsWebhook>(`/api/v1/admin/webhooks/events${queryString({ tenantId: id, page: 0, size: 10 })}`);

export async function setOpsTenantAccess(id: string, enabled: boolean): Promise<OpsTenant> {
  const token = getAdminToken();
  if (!token) throw new ApiClientError("Your admin session has expired.", 401, "ADMIN_SESSION_REQUIRED");
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/v1/admin/tenants/${encodeURIComponent(id)}/access`, {
      method: "PUT",
      cache: "no-store",
      headers: { Accept: "application/json", "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({ enabled }),
    });
  } catch {
    throw new ApiClientError("Unable to reach the operations API.", 0);
  }
  const raw = await response.text();
  let payload: ApiResponse<OpsTenant>;
  try { payload = JSON.parse(raw) as ApiResponse<OpsTenant>; }
  catch { throw new ApiClientError(`Operations API returned an invalid response (${response.status}).`, response.status); }
  if (!response.ok || !payload.success || payload.data === null) {
    throw new ApiClientError(payload.error?.message ?? "Operations request failed.", response.status, payload.error?.code);
  }
  return payload.data;
}

export async function getPublicPlatformStatus(): Promise<PlatformStatus | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/status`, { cache: "no-store", headers: { Accept: "application/json" } });
    const payload = (await response.json()) as ApiResponse<PlatformStatus>;
    return response.ok && payload.success ? payload.data : null;
  } catch { return null; }
}


export const provisionTenant = (request: { organization: string; email: string; password: string; planCode: string }) =>
  adminRequest<{ organization: string; email: string; tenantId: string }>("/api/v1/admin/tenants", request);
