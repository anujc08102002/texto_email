/**
 * Platform admin presentation types.
 * Replace with GET/POST /api/v1/admin/* when backend ships.
 */

export type AdminTenantStatus = "active" | "trial" | "past_due" | "suspended" | "sending_frozen" | "closed";

export type AdminTenant = {
  id: string;
  organization: string;
  slug: string;
  ownerEmail: string;
  status: AdminTenantStatus;
  planCode: string;
  planName: string;
  createdAt: string;
  lastActiveAt: string;
  users: number;
  domains: number;
  messages30d: number;
  usagePct: number;
  mrr: number;
  bounceRate: number;
  complaintRate: number;
  risk: "low" | "medium" | "high";
  notes?: string;
  sendingFrozen: boolean;
  features: {
    campaigns: boolean;
    webhooks: boolean;
    apiAccess: boolean;
    customDomains: boolean;
  };
  limits: {
    messages: number;
    domains: number;
    apiKeys: number;
  };
};

export type AdminAuditEvent = {
  id: string;
  at: string;
  actor: string;
  action: string;
  tenant: string;
  detail: string;
};

export type AdminBillingAlert = {
  id: string;
  tenantId: string;
  organization: string;
  kind: "past_due" | "failed_payment" | "quota_exceeded" | "trial_ending";
  amount?: number;
  daysOverdue?: number;
  message: string;
};

export type AdminComplianceItem = {
  id: string;
  tenantId: string;
  organization: string;
  severity: "low" | "medium" | "high";
  type: "spam_complaint" | "abuse_report" | "phishing_flag" | "manual_review";
  summary: string;
  createdAt: string;
  status: "open" | "investigating" | "resolved";
};

export type AdminMessageTemplate = {
  id: string;
  name: string;
  subject: string;
  body: string;
  channel: "billing" | "system" | "compliance";
};

export type AdminPlatformFlags = {
  maintenanceMode: boolean;
  newSignups: boolean;
  globalSendThrottle: boolean;
  requireDnsVerified: boolean;
};
