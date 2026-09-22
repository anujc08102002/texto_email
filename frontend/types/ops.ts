export type OpsPage<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type OpsDashboard = {
  tenantId: string | null;
  totalMessages: number;
  queued: number;
  processing: number;
  sending: number;
  delivered: number;
  deferred: number;
  failed: number;
  bounced: number;
  suppressed: number;
  complaints: number;
  deliveryRate: number;
  bounceRate: number;
  complaintRate: number;
  recentActivity: Array<{ at: string; type: string; tenantId: string; messageId: string; status: string; summary: string }>;
};

export type OpsEmail = {
  id: string; tenantId: string; status: string; statusReason: string | null; fromAddress: string;
  recipient: string; recipientsTo: string[]; recipientsCc: string[]; recipientsBcc: string[];
  suppressedRecipients: string[]; bouncedRecipients: string[]; providerMessageId: string | null;
  rfc822MessageId: string | null; attemptCount: number | null; maxAttempts: number | null;
  lastError: string | null; queuedAt: string | null; deliveredAt: string | null; failedAt: string | null;
  createdAt: string; updatedAt: string;
};

export type OpsAttempt = {
  id: string; tenantId: string; messageId: string; attemptNumber: number; status: string;
  errorCategory: string | null; errorMessage: string | null; providerResponse: string | null;
  startedAt: string; completedAt: string | null; createdAt: string;
};

export type OpsBounce = {
  id: string; tenantId: string; emailMessageId: string | null; correlationStatus: string; bounceClass: string;
  failureKind: string; statusCode: string; diagnosticCode: string; diagnosticMessage: string;
  originalRecipient: string; finalRecipient: string; provider: string; providerEventId: string;
  receivedAt: string; createdAt: string;
};

export type OpsComplaint = {
  id: string; tenantId: string; emailMessageId: string | null; correlationStatus: string; processingStatus: string;
  provider: string; providerEventId: string; complaintType: string; recipient: string; originalMessageId: string;
  providerMessageId: string; diagnostic: string; occurredAt: string; receivedAt: string; createdAt: string;
};

export type OpsSuppression = {
  id: string; tenantId: string; messageId: string | null; email: string; type: string; source: string;
  reason: string; createdAt: string; updatedAt: string;
};

export type OpsDomain = {
  id: string; tenantId: string; domain: string; status: string; verificationStatus: string; mailFromDomain: string;
  spf: OpsDnsRecord; dkim: OpsDnsRecord; dmarc: OpsDnsRecord; ownership: OpsDnsRecord;
  createdAt: string; updatedAt: string;
};

export type OpsDnsRecord = { type: string; name: string | null; status: string; selector: string | null; lastError: string | null; verifiedAt: string | null };
export type OpsTenant = { id: string; name: string; slug: string; status: string; createdAt: string; updatedAt: string };
export type OpsWebhook = { id: string; tenantId: string; webhookConfigId: string; sourceMessageId: string | null; eventType: string; status: string; attemptCount: number; lastResponseCode: number | null; lastError: string | null; nextAttemptAt: string | null; lastAttemptAt: string | null; deliveredAt: string | null; createdAt: string };
export type OpsOutbox = { id: string; tenantId: string; aggregateType: string; aggregateId: string; eventType: string; status: string; attempts: number; lastError: string | null; createdAt: string; publishedAt: string | null };
export type OpsTraffic = { id: string; requestId: string; traceId: string | null; timestamp: string; tenantId: string | null; principalId: string | null; apiKeyId: string | null; httpMethod: string; route: string; statusCode: number; durationMs: number; requestSizeBytes: number | null; responseSizeBytes: number | null; clientIp: string | null; userAgent: string | null; referer: string | null; authOutcome: string | null; errorCode: string | null; errorCategory: string | null; emailMessageId: string | null; providerMessageId: string | null; operationType: string | null; createdAt: string };
export type OpsAudit = { id: string; adminId: string; method: string; path: string; queryString: string | null; statusCode: number; durationMs: number; requestId: string; createdAt: string };
export type OpsQueueStatus = { queues: Array<{ name: string; messages: number; consumers: number; available: boolean }> };
export type OpsHealth = { status: string; components: Record<string, string> };
export type OpsTimelineEvent = { at: string; type: string; status: string; detail: string };
export type OpsEmailDetail = { message: OpsEmail; timestamps: Record<string, string>; timeline: OpsTimelineEvent[]; deliveryAttempts: OpsAttempt[]; bounces: OpsBounce[]; complaints: OpsComplaint[]; suppressions: OpsSuppression[] };

