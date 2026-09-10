import { apiDeleteEmpty, apiGet, apiPost } from "@/lib/api";
import type { Domain, DomainVerification } from "@/types/api";

export function listDomains() {
  return apiGet<Domain[]>("/api/v1/domains");
}

export function getDomain(id: string) {
  return apiGet<Domain>(`/api/v1/domains/${id}`);
}

export function createDomain(domain: string) {
  return apiPost<Domain>("/api/v1/domains", { domain });
}

export function deleteDomain(id: string) {
  return apiDeleteEmpty(`/api/v1/domains/${id}`);
}

export function verifyDomain(id: string) {
  return apiPost<Domain>(`/api/v1/domains/${id}/verify`);
}

export function getDomainVerification(id: string) {
  return apiGet<DomainVerification>(`/api/v1/domains/${id}/verification`);
}
