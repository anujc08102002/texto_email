/**
 * Temporary presentation types for unfinished backend modules.
 * Do not encode entitlements, delivery rules, or billing logic here.
 */

export type MetricTrend = "up" | "down" | "flat";
export type HealthTone = "operational" | "degraded" | "outage" | "unknown";

export type PresentationMetric = {
  id: string;
  label: string;
  value: string;
  change: string;
  trend: MetricTrend;
  comparison: string;
};

export type PresentationActivity = {
  id: string;
  recipient: string;
  subject: string;
  status: string;
  createdAt: string;
};

export type PresentationSystemStatus = {
  id: string;
  label: string;
  detail: string;
  tone: "success" | "warning" | "info";
};

export type PresentationUsage = {
  label: string;
  used: number;
  limit: number;
  unit: string;
};

export type PresentationAnalyticsPoint = {
  date: string;
  sent: number;
  delivered: number;
  bounced: number;
  failed: number;
  deferred?: number;
  openRate: number;
  clickRate: number;
};

export type PresentationApiKey = {
  id: string;
  name: string;
  prefix: string;
  environment: "live" | "test";
  status: "active" | "revoked";
  createdAt: string;
  lastUsedAt: string | null;
};

export type PresentationDomain = {
  id: string;
  domain: string;
  verification: "verified" | "pending" | "failed";
  spf: "pass" | "pending" | "fail";
  dkim: "pass" | "pending" | "fail";
  dmarc: "pass" | "pending" | "fail";
  reputation?: number | null;
};

export type PresentationDnsRecord = {
  type: string;
  name: string;
  value: string;
  ttl: string;
  status: "verified" | "pending" | "missing";
};

export type PresentationSubscription = {
  planName: string;
  planCode: string;
  status: "active" | "past_due" | "canceled" | "incomplete";
  periodStart: string;
  periodEnd: string;
  paymentStatus: "paid" | "open" | "failed";
};

export type PresentationInfraNode = {
  id: string;
  label: string;
  status: HealthTone;
  meta: string;
  latencyMs?: number | null;
};

export type PresentationQueueSnapshot = {
  queued: number;
  processing: number;
  deferred: number;
  retrying: number;
  averageLatencyMs: number;
};

export type PresentationDeliveryBreakdown = {
  delivered: number;
  deferred: number;
  bounced: number;
  failed: number;
};

export type PresentationStreamEvent = {
  id: string;
  status: "DELIVERED" | "SENDING" | "DEFERRED" | "FAILED" | "QUEUED" | "PROCESSING";
  recipient: string;
  subject: string;
  latencyMs: number | null;
  timestamp: string;
  detail?: string | null;
};

export type PresentationAttentionItem = {
  id: string;
  severity: "warning" | "error" | "info";
  title: string;
  description: string;
  href?: string;
};

export type PresentationUsageSnapshot = {
  used: number;
  limit: number;
  resetsInDays: number;
  domainsUsed: number;
  domainsLimit: number;
  apiKeysUsed: number;
  apiKeysLimit: number;
  templatesUsed: number;
  templatesLimit: number;
};

export type QueryState = "loading" | "empty" | "error" | "ready";
