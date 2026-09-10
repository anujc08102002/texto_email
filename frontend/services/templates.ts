import { apiDelete, apiGet, apiPatch, apiPost } from "@/lib/api";
import type { Template, TemplateVersion } from "@/types/api";

export type CreateTemplateInput = {
  name: string;
  description?: string;
  subject: string;
  htmlContent: string;
  textContent?: string;
  variablesSchema?: Record<string, unknown>;
};

export type CreateTemplateVersionInput = {
  subject: string;
  htmlContent: string;
  textContent?: string;
  variablesSchema?: Record<string, unknown>;
};

export function listTemplates() {
  return apiGet<Template[]>("/api/v1/templates");
}

export function getTemplate(id: string) {
  return apiGet<Template>(`/api/v1/templates/${id}`);
}

export function createTemplate(input: CreateTemplateInput) {
  return apiPost<Template>("/api/v1/templates", input);
}

export function patchTemplate(id: string, input: { name?: string; description?: string }) {
  return apiPatch<Template>(`/api/v1/templates/${id}`, input);
}

export function archiveTemplate(id: string) {
  return apiPost<Template>(`/api/v1/templates/${id}/archive`);
}

export function deleteTemplate(id: string) {
  return apiDelete<Template>(`/api/v1/templates/${id}`);
}

export function listTemplateVersions(id: string) {
  return apiGet<TemplateVersion[]>(`/api/v1/templates/${id}/versions`);
}

export function getTemplateVersion(id: string, version: number) {
  return apiGet<TemplateVersion>(`/api/v1/templates/${id}/versions/${version}`);
}

export function createTemplateVersion(id: string, input: CreateTemplateVersionInput) {
  return apiPost<TemplateVersion>(`/api/v1/templates/${id}/versions`, input);
}

export function activateTemplateVersion(id: string, version: number) {
  return apiPost<Template>(`/api/v1/templates/${id}/versions/${version}/activate`);
}
