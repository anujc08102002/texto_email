package com.texto.emailplatform.email.domain;

import com.texto.emailplatform.email.MessageStateMachine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "email_messages")
public class EmailMessageEntity {

    public static final String STATUS_QUEUED = MessageStateMachine.QUEUED;
    public static final String STATUS_PROCESSING = MessageStateMachine.PROCESSING;
    public static final String STATUS_SENDING = MessageStateMachine.SENDING;
    public static final String STATUS_DELIVERED = MessageStateMachine.DELIVERED;
    public static final String STATUS_DEFERRED = MessageStateMachine.DEFERRED;
    public static final String STATUS_FAILED = MessageStateMachine.FAILED;
    public static final String STATUS_BOUNCED = MessageStateMachine.BOUNCED;
    public static final String STATUS_SUPPRESSED = MessageStateMachine.SUPPRESSED;
    public static final String STATUS_CANCELLED = MessageStateMachine.CANCELLED;
    public static final String STATUS_EXPIRED = MessageStateMachine.EXPIRED;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_version_id")
    private UUID templateVersionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "status_reason", length = 128)
    private String statusReason;

    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    @Column(name = "from_address", length = 320)
    private String fromAddress;

    @Column(name = "reply_to", length = 320)
    private String replyTo;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipients_to", nullable = false, columnDefinition = "jsonb")
    private List<String> recipientsTo = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipients_cc", nullable = false, columnDefinition = "jsonb")
    private List<String> recipientsCc = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipients_bcc", nullable = false, columnDefinition = "jsonb")
    private List<String> recipientsBcc = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suppressed_recipients", nullable = false, columnDefinition = "jsonb")
    private List<String> suppressedRecipients = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "html_body")
    private String htmlBody;

    @Column(name = "text_body")
    private String textBody;

    @Column(name = "provider_response")
    private String providerResponse;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "sending_at")
    private Instant sendingAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EmailMessageEntity create(
            UUID tenantId,
            String fromAddress,
            List<String> recipientsTo,
            List<String> recipientsCc,
            List<String> recipientsBcc,
            List<String> suppressedRecipients,
            String replyTo,
            String subject,
            String htmlBody,
            String textBody,
            UUID templateId,
            UUID templateVersionId,
            String idempotencyKey,
            Map<String, Object> metadata,
            int maxAttempts,
            UUID createdBy
    ) {
        Instant now = Instant.now();
        EmailMessageEntity entity = new EmailMessageEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.fromAddress = fromAddress;
        entity.replyTo = replyTo;
        entity.idempotencyKey = idempotencyKey;
        entity.recipientsTo = copyList(recipientsTo);
        entity.recipientsCc = copyList(recipientsCc);
        entity.recipientsBcc = copyList(recipientsBcc);
        entity.suppressedRecipients = copyList(suppressedRecipients);
        entity.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        entity.subject = subject;
        entity.htmlBody = htmlBody;
        entity.textBody = textBody;
        entity.templateId = templateId;
        entity.templateVersionId = templateVersionId;
        entity.attemptCount = 0;
        entity.maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
        entity.createdBy = createdBy;
        entity.recipient = primaryRecipient(entity.recipientsTo, entity.recipientsCc, entity.recipientsBcc, entity.suppressedRecipients);
        entity.status = STATUS_QUEUED;
        entity.queuedAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void markQueued() {
        MessageStateMachine.assertTransition(this.status, STATUS_QUEUED);
        this.status = STATUS_QUEUED;
        this.queuedAt = Instant.now();
        this.nextAttemptAt = null;
        touch();
    }

    public void markProcessing() {
        MessageStateMachine.assertTransition(this.status, STATUS_PROCESSING);
        this.status = STATUS_PROCESSING;
        this.processingAt = Instant.now();
        this.nextAttemptAt = null;
        touch();
    }

    public void markSending() {
        MessageStateMachine.assertTransition(this.status, STATUS_SENDING);
        this.status = STATUS_SENDING;
        this.sendingAt = Instant.now();
        touch();
    }

    public void markDelivered(String providerMessageId, String providerResponse) {
        MessageStateMachine.assertTransition(this.status, STATUS_DELIVERED);
        this.status = STATUS_DELIVERED;
        this.providerMessageId = providerMessageId;
        this.providerResponse = truncate(providerResponse);
        this.deliveredAt = Instant.now();
        this.lastError = null;
        this.nextAttemptAt = null;
        touch();
    }

    /** Backward-compatible alias. */
    public void markDelivered() {
        markDelivered(this.providerMessageId, this.providerResponse);
    }

    /** Backward-compatible alias — maps to {@link #markDelivered()}. */
    public void markSent() {
        markDelivered();
    }

    public void markDeferred(String reason, String providerResponse, Instant nextAttemptAt) {
        MessageStateMachine.assertTransition(this.status, STATUS_DEFERRED);
        this.status = STATUS_DEFERRED;
        this.statusReason = reason;
        this.providerResponse = truncate(providerResponse);
        this.lastError = truncate(reason);
        this.nextAttemptAt = nextAttemptAt;
        touch();
    }

    public void markDeferred(String reason, String providerResponse) {
        markDeferred(reason, providerResponse, null);
    }

    public void markFailed(String reason, String providerResponse) {
        MessageStateMachine.assertTransition(this.status, STATUS_FAILED);
        this.status = STATUS_FAILED;
        this.statusReason = reason;
        this.providerResponse = truncate(providerResponse);
        this.lastError = truncate(reason);
        this.failedAt = Instant.now();
        this.nextAttemptAt = null;
        touch();
    }

    public void markFailed() {
        markFailed("delivery_failed", null);
    }

    public void markBounced(String reason, String providerResponse) {
        MessageStateMachine.assertTransition(this.status, STATUS_BOUNCED);
        this.status = STATUS_BOUNCED;
        this.statusReason = reason;
        this.providerResponse = truncate(providerResponse);
        this.lastError = truncate(reason);
        this.failedAt = Instant.now();
        this.nextAttemptAt = null;
        touch();
    }

    public void markSuppressed(String reason) {
        MessageStateMachine.assertTransition(this.status, STATUS_SUPPRESSED);
        this.status = STATUS_SUPPRESSED;
        this.statusReason = reason;
        this.lastError = truncate(reason);
        touch();
    }

    public void incrementAttempt() {
        this.attemptCount++;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static List<String> copyList(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static String primaryRecipient(
            List<String> to,
            List<String> cc,
            List<String> bcc,
            List<String> suppressed
    ) {
        if (!to.isEmpty()) {
            return to.getFirst();
        }
        if (!cc.isEmpty()) {
            return cc.getFirst();
        }
        if (!bcc.isEmpty()) {
            return bcc.getFirst();
        }
        if (!suppressed.isEmpty()) {
            return suppressed.getFirst();
        }
        return "";
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<String> getRecipientsTo() {
        return recipientsTo;
    }

    public List<String> getRecipientsCc() {
        return recipientsCc;
    }

    public List<String> getRecipientsBcc() {
        return recipientsBcc;
    }

    public List<String> getSuppressedRecipients() {
        return suppressedRecipients;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getSubject() {
        return subject;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public String getTextBody() {
        return textBody;
    }

    public String getProviderResponse() {
        return providerResponse;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getProcessingAt() {
        return processingAt;
    }

    public Instant getSendingAt() {
        return sendingAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
