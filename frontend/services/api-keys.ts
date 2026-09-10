import { apiGet, apiPost } from "@/lib/api";
import type { ApiKey, CreatedApiKey } from "@/types/api";

export function listApiKeys() {
  return apiGet<ApiKey[]>("/api/v1/api-keys");
}

export function getApiKey(id: string) {
  return apiGet<ApiKey>(`/api/v1/api-keys/${id}`);
}

export function createApiKey(payload: { name: string; environment: "TEST" | "LIVE" }) {
  return apiPost<CreatedApiKey>("/api/v1/api-keys", payload);
}

export function revokeApiKey(id: string) {
  return apiPost<ApiKey>(`/api/v1/api-keys/${id}/revoke`);
}
