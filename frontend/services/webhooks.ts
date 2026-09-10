import { apiDeleteEmpty, apiGet, apiPatch, apiPost } from "@/lib/api";
import type { CreatedWebhook, WebhookConfig, WebhookEvent } from "@/types/api";

export const WEBHOOK_EVENT_TYPES = [
  "email.queued",
  "email.sending",
  "email.delivered",
  "email.deferred",
  "email.bounced",
  "email.failed",
  "email.suppressed",
] as const;

export function listWebhooks() {
  return apiGet<WebhookConfig[]>("/api/v1/webhooks");
}

export function getWebhook(id: string) {
  return apiGet<WebhookConfig>(`/api/v1/webhooks/${id}`);
}

export function createWebhook(input: { url: string; description?: string; eventTypes: string[] }) {
  return apiPost<CreatedWebhook>("/api/v1/webhooks", input);
}

export function patchWebhook(
  id: string,
  input: { url?: string; description?: string; eventTypes?: string[] },
) {
  return apiPatch<WebhookConfig>(`/api/v1/webhooks/${id}`, input);
}

export function pauseWebhook(id: string) {
  return apiPost<WebhookConfig>(`/api/v1/webhooks/${id}/pause`);
}

export function resumeWebhook(id: string) {
  return apiPost<WebhookConfig>(`/api/v1/webhooks/${id}/resume`);
}

export function rotateWebhookSecret(id: string) {
  return apiPost<CreatedWebhook>(`/api/v1/webhooks/${id}/rotate-secret`);
}

export function testWebhook(id: string) {
  return apiPost<WebhookEvent>(`/api/v1/webhooks/${id}/test`);
}

export function deleteWebhook(id: string) {
  return apiDeleteEmpty(`/api/v1/webhooks/${id}`);
}

export function listWebhookEvents(id: string) {
  return apiGet<WebhookEvent[]>(`/api/v1/webhooks/${id}/events`);
}
