/**
 * TEMPORARY PRESENTATION DATA
 *
 * Isolated placeholders for screens whose backend endpoints are unfinished.
 * Replace with tenant-scoped API responses. Not product truth.
 */

import type {
  PresentationAnalyticsPoint,
  PresentationApiKey,
  PresentationAttentionItem,
  PresentationDeliveryBreakdown,
  PresentationDnsRecord,
  PresentationDomain,
  PresentationInfraNode,
  PresentationQueueSnapshot,
  PresentationStreamEvent,
  PresentationSubscription,
  PresentationUsageSnapshot,
} from "@/types/presentation";

export const PRESENTATION_NOTICE =
  "Temporary presentation data · Not product metrics. Replace with tenant-scoped API responses.";

export const ANALYTICS_SERIES: PresentationAnalyticsPoint[] = [
  { date: "00:00", sent: 820, delivered: 804, bounced: 8, failed: 3, deferred: 5, openRate: 41, clickRate: 5 },
  { date: "04:00", sent: 640, delivered: 628, bounced: 6, failed: 2, deferred: 4, openRate: 38, clickRate: 4 },
  { date: "08:00", sent: 1420, delivered: 1388, bounced: 14, failed: 6, deferred: 12, openRate: 46, clickRate: 7 },
  { date: "12:00", sent: 1980, delivered: 1931, bounced: 21, failed: 9, deferred: 19, openRate: 52, clickRate: 8 },
  { date: "16:00", sent: 1760, delivered: 1718, bounced: 18, failed: 7, deferred: 17, openRate: 49, clickRate: 7 },
  { date: "20:00", sent: 1210, delivered: 1184, bounced: 11, failed: 4, deferred: 11, openRate: 44, clickRate: 6 },
  { date: "23:00", sent: 980, delivered: 962, bounced: 9, failed: 3, deferred: 6, openRate: 42, clickRate: 5 },
];

export const WEEKLY_SERIES: PresentationAnalyticsPoint[] = [
  { date: "Mon", sent: 6240, delivered: 6102, bounced: 58, failed: 22, deferred: 58, openRate: 48, clickRate: 6 },
  { date: "Tue", sent: 7120, delivered: 6978, bounced: 64, failed: 24, deferred: 54, openRate: 51, clickRate: 7 },
  { date: "Wed", sent: 6810, delivered: 6671, bounced: 61, failed: 21, deferred: 57, openRate: 49, clickRate: 7 },
  { date: "Thu", sent: 7540, delivered: 7388, bounced: 68, failed: 27, deferred: 57, openRate: 53, clickRate: 8 },
  { date: "Fri", sent: 7020, delivered: 6874, bounced: 63, failed: 25, deferred: 58, openRate: 50, clickRate: 7 },
  { date: "Sat", sent: 3180, delivered: 3114, bounced: 28, failed: 10, deferred: 28, openRate: 41, clickRate: 4 },
  { date: "Sun", sent: 2840, delivered: 2784, bounced: 24, failed: 9, deferred: 23, openRate: 39, clickRate: 3 },
];

export const PRIMARY_SENT = {
  value: 48291,
  changePct: 12.8,
  trend: "up" as const,
  transactional: 36102,
  marketing: 9840,
  test: 2349,
};

export const DELIVERY_RATE = {
  value: 98.72,
  changePct: 0.31,
  trend: "up" as const,
  sparkline: [97.9, 98.1, 98.0, 98.4, 98.3, 98.6, 98.72],
};

export const DELIVERY_BREAKDOWN: PresentationDeliveryBreakdown = {
  delivered: 98.72,
  deferred: 0.91,
  bounced: 0.34,
  failed: 0.03,
};

export const INFRA_NODES: PresentationInfraNode[] = [
  { id: "api", label: "API", status: "operational", meta: "Public edge", latencyMs: 42 },
  { id: "queue", label: "Queue", status: "operational", meta: "1,204 messages", latencyMs: null },
  { id: "workers", label: "Workers", status: "operational", meta: "12 active", latencyMs: null },
  { id: "smtp", label: "SMTP", status: "operational", meta: "Mailpit sink", latencyMs: 182 },
  { id: "webhooks", label: "Webhooks", status: "operational", meta: "0 pending", latencyMs: 96 },
  { id: "database", label: "Database", status: "operational", meta: "PostgreSQL", latencyMs: 8 },
];

export const QUEUE_SNAPSHOT: PresentationQueueSnapshot = {
  queued: 1204,
  processing: 842,
  deferred: 31,
  retrying: 12,
  averageLatencyMs: 182,
};

export const LIVE_STREAM: PresentationStreamEvent[] = [
  {
    id: "evt_1",
    status: "DELIVERED",
    recipient: "john@gmail.com",
    subject: "welcome-email",
    latencyMs: 184,
    timestamp: "09:42:18",
  },
  {
    id: "evt_2",
    status: "SENDING",
    recipient: "customer@outlook.com",
    subject: "invoice",
    latencyMs: null,
    timestamp: "09:42:17",
  },
  {
    id: "evt_3",
    status: "DEFERRED",
    recipient: "test@example.com",
    subject: "campaign",
    latencyMs: 842,
    timestamp: "09:42:11",
    detail: "421 Temporary Failure",
  },
  {
    id: "evt_4",
    status: "DELIVERED",
    recipient: "ops@acme.io",
    subject: "password-reset",
    latencyMs: 129,
    timestamp: "09:42:04",
  },
  {
    id: "evt_5",
    status: "FAILED",
    recipient: "bounce@invalid.test",
    subject: "receipt",
    latencyMs: 410,
    timestamp: "09:41:58",
    detail: "550 User unknown",
  },
  {
    id: "evt_6",
    status: "PROCESSING",
    recipient: "team@startup.dev",
    subject: "invite",
    latencyMs: null,
    timestamp: "09:41:55",
  },
];

export const USAGE_SNAPSHOT: PresentationUsageSnapshot = {
  used: 38291,
  limit: 50000,
  resetsInDays: 12,
  domainsUsed: 2,
  domainsLimit: 5,
  apiKeysUsed: 3,
  apiKeysLimit: 10,
  templatesUsed: 8,
  templatesLimit: 50,
};

export const DOMAIN_HEALTH: PresentationDomain[] = [
  {
    id: "dom_1",
    domain: "example.com",
    verification: "verified",
    spf: "pass",
    dkim: "pass",
    dmarc: "pass",
    reputation: 98,
  },
  {
    id: "dom_2",
    domain: "another.com",
    verification: "pending",
    spf: "pass",
    dkim: "pending",
    dmarc: "pass",
    reputation: null,
  },
];

export const ATTENTION_ITEMS: PresentationAttentionItem[] = [
  {
    id: "att_1",
    severity: "warning",
    title: "DKIM verification required",
    description: "another.com is pending DKIM confirmation.",
    href: "/domains",
  },
  {
    id: "att_2",
    severity: "info",
    title: "Quota approaching limit",
    description: "76.6% of monthly message entitlement used.",
    href: "/billing",
  },
  {
    id: "att_3",
    severity: "warning",
    title: "12 deferred messages",
    description: "Temporary SMTP failures awaiting retry.",
    href: "/emails",
  },
];

/** Prefixes only — never store full secrets in presentation data */
export const API_KEYS: PresentationApiKey[] = [
  {
    id: "key_1",
    name: "Production sending",
    prefix: "tex_live_8f2a",
    environment: "live",
    status: "active",
    createdAt: "2026-08-12T10:22:00Z",
    lastUsedAt: "2026-09-08T03:41:00Z",
  },
  {
    id: "key_2",
    name: "Staging integration",
    prefix: "tex_test_91bc",
    environment: "test",
    status: "active",
    createdAt: "2026-07-01T08:00:00Z",
    lastUsedAt: "2026-09-07T18:12:00Z",
  },
  {
    id: "key_3",
    name: "Legacy webhook signer",
    prefix: "tex_live_0041",
    environment: "live",
    status: "revoked",
    createdAt: "2026-03-18T14:05:00Z",
    lastUsedAt: null,
  },
];

export const DOMAINS: PresentationDomain[] = DOMAIN_HEALTH;

export const DOMAIN_DNS_RECORDS: PresentationDnsRecord[] = [
  { type: "TXT", name: "@", value: "v=spf1 include:_spf.texto.dev ~all", ttl: "3600", status: "pending" },
  { type: "CNAME", name: "texto._domainkey", value: "dkim.texto.dev", ttl: "3600", status: "pending" },
  { type: "TXT", name: "_dmarc", value: "v=DMARC1; p=none; rua=mailto:dmarc@texto.dev", ttl: "3600", status: "pending" },
];

/** Preview subscription shape — replace with GET /api/v1/billing */
export const SUBSCRIPTION: PresentationSubscription | null = {
  planName: "Growth",
  planCode: "growth",
  status: "active",
  periodStart: "2026-09-01",
  periodEnd: "2026-09-30",
  paymentStatus: "paid",
};

/** Legacy aliases used by older screens */
export const DASHBOARD_METRICS = [
  { id: "sent", label: "Emails sent", value: "—", change: "Awaiting API", trend: "flat" as const, comparison: "Last 30 days" },
  { id: "delivery", label: "Delivery rate", value: "—", change: "Awaiting API", trend: "flat" as const, comparison: "Last 30 days" },
  { id: "bounce", label: "Bounce rate", value: "—", change: "Awaiting API", trend: "flat" as const, comparison: "Last 30 days" },
  { id: "open", label: "Open rate", value: "—", change: "Awaiting API", trend: "flat" as const, comparison: "Last 30 days" },
];

export const DASHBOARD_SYSTEM = [
  { id: "api", label: "Public API", detail: "Connected via /api/v1/status", tone: "info" as const },
  { id: "smtp", label: "Local delivery", detail: "Mailpit sink on port 1025", tone: "info" as const },
  { id: "queue", label: "Outbound queue", detail: "RabbitMQ topology is provisioned", tone: "info" as const },
];
