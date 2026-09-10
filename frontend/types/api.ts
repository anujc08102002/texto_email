export type ApiError = {
  code: string;
  message: string;
  details?: Array<{ field: string; message: string }>;
};

export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  error: ApiError | null;
  meta: {
    requestId: string;
    timestamp: string;
  };
};

export type Plan = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  sortOrder?: number;
  features?: Record<string, boolean>;
  limits?: Record<string, number | null>;
};

export type PlatformStatus = {
  name: string;
  version: string;
  status: string;
};

export type AuthUser = {
  userId: string;
  tenantId: string;
  email: string;
  role: string;
  organization: string;
};

export type AuthSession = {
  token: string;
  tokenType: string;
  user: AuthUser;
};

export type EmailStatus =
  | "QUEUED"
  | "PROCESSING"
  | "SENDING"
  | "DELIVERED"
  | "DEFERRED"
  | "FAILED"
  | "BOUNCED"
  | "SUPPRESSED"
  | "CANCELLED"
  | "EXPIRED"
  | "SENT"
  | string;

export type DeliveryAttempt = {
  id: string;
  attemptNumber: number;
  status: string;
  startedAt: string;
  completedAt: string | null;
  providerResponse: string | null;
  errorCategory: string | null;
  errorMessage: string | null;
};

export type EmailMessage = {
  id: string;
  status: EmailStatus;
  recipient: string;
  fromAddress?: string | null;
  replyTo?: string | null;
  subject: string;
  statusReason?: string | null;
  recipientsTo?: string[];
  recipientsCc?: string[];
  recipientsBcc?: string[];
  suppressedRecipients?: string[];
  metadata?: Record<string, unknown> | null;
  providerMessageId?: string | null;
  attemptCount?: number | null;
  maxAttempts?: number | null;
  nextAttemptAt?: string | null;
  queuedAt?: string | null;
  deliveredAt?: string | null;
  failedAt?: string | null;
  lastError?: string | null;
  templateId?: string | null;
  templateVersionId?: string | null;
  createdAt: string;
};

export type EmailMessageDetail = EmailMessage & {
  processingAt?: string | null;
  sendingAt?: string | null;
  updatedAt?: string | null;
  attempts?: DeliveryAttempt[];
  htmlBody?: string | null;
  textBody?: string | null;
};

export type EmailMessagePage = {
  items: EmailMessage[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type EmailComposerPayload = {
  from: string;
  to: string[];
  cc?: string[];
  bcc?: string[];
  replyTo?: string;
  subject: string;
  text?: string;
  html?: string;
};

export type Subscription = {
  id: string;
  tenantId: string;
  planId: string;
  planCode: string;
  planName: string;
  status: string;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  trialStart: string | null;
  trialEnd: string | null;
  cancelAtPeriodEnd: boolean;
  cancelledAt: string | null;
  createdAt: string;
  provider?: string | null;
  providerSubscriptionId?: string | null;
  pendingPlanCode?: string | null;
  gracePeriodEndsAt?: string | null;
};

export type Entitlements = {
  tenantId: string;
  planCode: string;
  planName: string;
  subscriptionStatus: string;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  features: Record<string, boolean>;
  limits: Record<string, number | null>;
  usage: Record<string, number>;
};

export type UsageMetric = {
  metric: string;
  used: number;
  limit: number | null;
  remaining: number | null;
};

export type UsageSnapshot = {
  tenantId: string;
  periodStart: string;
  periodEnd: string;
  metrics: UsageMetric[];
};

export type ApiKey = {
  id: string;
  name: string;
  keyPrefix: string;
  environment: "TEST" | "LIVE" | string;
  status: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
};

export type CreatedApiKey = {
  apiKey: ApiKey;
  secret: string;
};

export type TemplateVariableSchema = Record<
  string,
  {
    type?: string;
    required?: boolean;
    description?: string;
  } | Record<string, unknown>
>;

export type Template = {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  status: "DRAFT" | "ACTIVE" | "ARCHIVED" | string;
  currentVersionId: string | null;
  currentVersion: number | null;
  createdAt: string;
  updatedAt: string;
};

export type TemplateVersion = {
  id: string;
  templateId: string;
  version: number;
  subject: string;
  htmlContent: string;
  textContent: string | null;
  variablesSchema: TemplateVariableSchema;
  createdAt: string;
};

export type Domain = {
  id: string;
  domain: string;
  status: string;
  verificationStatus: string;
  createdAt: string;
  updatedAt: string;
};

export type DomainVerificationRecord = {
  id: string;
  type: "SPF" | "DKIM" | "DMARC" | string;
  name: string;
  value: string;
  status: string;
  selector: string | null;
  publicKey: string | null;
  verifiedAt: string | null;
};

export type DomainVerification = {
  domainId: string;
  domain: string;
  status: string;
  records: DomainVerificationRecord[];
};

export type Suppression = {
  id: string;
  email: string;
  type: "BOUNCE" | "COMPLAINT" | "UNSUBSCRIBE" | "MANUAL" | string;
  reason: string;
  source: string;
  messageId: string | null;
  createdAt: string;
};

export type ImportSuppressionsResult = {
  imported: number;
  skipped: number;
};

export type WebhookConfig = {
  id: string;
  url: string;
  description: string | null;
  status: "ACTIVE" | "PAUSED" | "DISABLED" | string;
  secretPrefix: string;
  eventTypes: string[];
  createdAt: string;
  updatedAt: string;
};

export type CreatedWebhook = {
  webhook: WebhookConfig;
  secret: string;
};

export type WebhookEvent = {
  id: string;
  eventType: string;
  status: string;
  attemptCount: number;
  nextAttemptAt: string | null;
  lastAttemptAt: string | null;
  deliveredAt: string | null;
  lastResponseCode: number | null;
  lastError: string | null;
  sourceMessageId: string | null;
  payload: Record<string, unknown>;
  createdAt: string;
};
