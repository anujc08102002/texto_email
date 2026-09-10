import { apiDeleteEmpty, apiGet, apiPost } from "@/lib/api";
import type { Suppression } from "@/types/api";

export function listSuppressions(params?: { search?: string; type?: string }) {
  const query = new URLSearchParams();
  if (params?.search?.trim()) query.set("search", params.search.trim());
  if (params?.type && params.type !== "all") query.set("type", params.type);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiGet<Suppression[]>(`/api/v1/suppressions${suffix}`);
}

export function getSuppression(id: string) {
  return apiGet<Suppression>(`/api/v1/suppressions/${id}`);
}

export function createSuppression(input: { email: string; reason?: string }) {
  return apiPost<Suppression>("/api/v1/suppressions", input);
}

export function deleteSuppression(id: string) {
  return apiDeleteEmpty(`/api/v1/suppressions/${id}`);
}

export function importSuppressions(emails: string[]) {
  return apiPost<{ imported: number; skipped: number }>("/api/v1/suppressions/import", { emails });
}
