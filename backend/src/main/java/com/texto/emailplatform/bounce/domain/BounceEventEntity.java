package com.texto.emailplatform.bounce.domain;

import com.texto.emailplatform.bounce.BounceClass;
import com.texto.emailplatform.bounce.BounceFailureKind;
import com.texto.emailplatform.bounce.DsnAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bounce_events")
public class BounceEventEntity {

    public static final String CORRELATION_MATCHED = "MATCHED";
    public static final String CORRELATION_UNMATCHED = "UNMATCHED";
    public static final String CORRELATION_PARSE_FAILED = "PARSE_FAILED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "email_message_id")
    private UUID emailMessageId;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    @Column(name = "correlation_status", nullable = false, length = 32)
    private String correlationStatus;

    @Column(name = "bounce_class", nullable = false, length = 32)
    private String bounceClass;

    @Column(name = "failure_kind", nullable = false, length = 32)
    private String failureKind;

    @Column(name = "dsn_action", length = 32)
    private String dsnAction;

    @Column(name = "status_code", length = 32)
    private String statusCode;

    @Column(name = "diagnostic_code", length = 64)
    private String diagnosticCode;

    @Column(name = "diagnostic_message", length = 512)
    private String diagnosticMessage;

    @Column(name = "original_recipient", length = 320)
    private String originalRecipient;

    @Column(name = "final_recipient", length = 320)
    private String finalRecipient;

    @Column(name = "original_sender", length = 320)
    private String originalSender;

    @Column(name = "original_message_id", length = 255)
    private String originalMessageId;

    @Column(name = "reporting_mta", length = 255)
    private String reportingMta;

    @Column(name = "remote_mta", length = 255)
    private String remoteMta;

    @Column(name = "arrival_date")
    private Instant arrivalDate;

    @Column(name = "last_attempt_date")
    private Instant lastAttemptDate;

    @Column(name = "will_retry_until")
    private Instant willRetryUntil;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static BounceEventEntity create(
            UUID tenantId,
            UUID emailMessageId,
            String eventHash,
            String correlationStatus,
            BounceClass bounceClass,
            BounceFailureKind failureKind,
            DsnAction dsnAction,
            String statusCode,
            String diagnosticCode,
            String diagnosticMessage,
            String originalRecipient,
            String finalRecipient,
            String originalSender,
            String originalMessageId,
            String reportingMta,
            String remoteMta,
            Instant arrivalDate,
            Instant lastAttemptDate,
            Instant willRetryUntil
    ) {
        Instant now = Instant.now();
        BounceEventEntity entity = new BounceEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.emailMessageId = emailMessageId;
        entity.eventHash = eventHash;
        entity.correlationStatus = correlationStatus;
        entity.bounceClass = bounceClass == null ? BounceClass.UNKNOWN.name() : bounceClass.name();
        entity.failureKind = failureKind == null ? BounceFailureKind.UNKNOWN.name() : failureKind.name();
        entity.dsnAction = dsnAction == null ? null : dsnAction.name();
        entity.statusCode = clip(statusCode, 32);
        entity.diagnosticCode = clip(diagnosticCode, 64);
        entity.diagnosticMessage = clip(diagnosticMessage, 512);
        entity.originalRecipient = clip(originalRecipient, 320);
        entity.finalRecipient = clip(finalRecipient, 320);
        entity.originalSender = clip(originalSender, 320);
        entity.originalMessageId = clip(originalMessageId, 255);
        entity.reportingMta = clip(reportingMta, 255);
        entity.remoteMta = clip(remoteMta, 255);
        entity.arrivalDate = arrivalDate;
        entity.lastAttemptDate = lastAttemptDate;
        entity.willRetryUntil = willRetryUntil;
        entity.receivedAt = now;
        entity.createdAt = now;
        return entity;
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getEmailMessageId() {
        return emailMessageId;
    }

    public String getEventHash() {
        return eventHash;
    }

    public String getCorrelationStatus() {
        return correlationStatus;
    }

    public String getBounceClass() {
        return bounceClass;
    }

    public String getFailureKind() {
        return failureKind;
    }

    public String getDsnAction() {
        return dsnAction;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getDiagnosticCode() {
        return diagnosticCode;
    }

    public String getDiagnosticMessage() {
        return diagnosticMessage;
    }

    public String getOriginalRecipient() {
        return originalRecipient;
    }

    public String getFinalRecipient() {
        return finalRecipient;
    }

    public String getOriginalSender() {
        return originalSender;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public String getReportingMta() {
        return reportingMta;
    }

    public String getRemoteMta() {
        return remoteMta;
    }

    public Instant getArrivalDate() {
        return arrivalDate;
    }

    public Instant getLastAttemptDate() {
        return lastAttemptDate;
    }

    public Instant getWillRetryUntil() {
        return willRetryUntil;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
