import { apiGet, apiPost } from "@/lib/api";
import type { EmailMessage, EmailMessageDetail, EmailMessagePage } from "@/types/api";

export type SendEmailInput = {
  from: string;
  to: string[];
  cc?: string[];
  bcc?: string[];
  replyTo?: string;
  subject: string;
  text?: string;
  html?: string;
  metadata?: Record<string, unknown>;
};

export function listEmails(params?: { page?: number; size?: number; status?: string; q?: string }) {
  const query = new URLSearchParams();
  if (params?.page != null) query.set("page", String(params.page));
  if (params?.size != null) query.set("size", String(params.size));
  if (params?.status && params.status !== "all") query.set("status", params.status);
  if (params?.q?.trim()) query.set("q", params.q.trim());
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiGet<EmailMessagePage>(`/api/v1/emails${suffix}`);
}

export function getEmail(id: string) {
  return apiGet<EmailMessageDetail>(`/api/v1/emails/${id}`);
}

export function sendEmail(payload: SendEmailInput, idempotencyKey?: string) {
  return apiPost<EmailMessage>(
    "/api/v1/emails",
    payload,
    idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
  );
}

export function sendTestEmail(payload: {
  from: string;
  to: string;
  subject: string;
  body: string;
}) {
  return apiPost<EmailMessage>("/api/v1/emails/test", payload);
}
