package com.texto.emailplatform.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "complaint_events")
public class ComplaintEventEntity {

    public static final String CORRELATION_MATCHED = "MATCHED";
    public static final String CORRELATION_UNMATCHED = "UNMATCHED";
    public static final String CORRELATION_PARSE_FAILED = "PARSE_FAILED";

    public static final String PROCESSING_APPLIED = "APPLIED";
    public static final String PROCESSING_NO_ACTION = "NO_ACTION";
    public static final String PROCESSING_UNCORRELATED = "UNCORRELATED";
    public static final String PROCESSING_PARSE_FAILED = "PARSE_FAILED";

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

    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "provider_event_id", length = 128)
    private String providerEventId;

    @Column(name = "complaint_type", nullable = false, length = 32)
    private String complaintType;

    @Column(name = "recipient", length = 320)
    private String recipient;

    @Column(name = "stored_recipient", length = 320)
    private String storedRecipient;

    @Column(name = "original_message_id", length = 255)
    private String originalMessageId;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "correlation_token_fp", length = 8)
    private String correlationTokenFp;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "diagnostic", length = 512)
    private String diagnostic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ComplaintEventEntity create(
            UUID tenantId,
            UUID emailMessageId,
            String eventHash,
            String correlationStatus,
            String processingStatus,
            String provider,
            String providerEventId,
            String complaintType,
            String recipient,
            String storedRecipient,
            String originalMessageId,
            String providerMessageId,
            String correlationTokenFp,
            Instant occurredAt,
            String diagnostic,
            Map<String, String> metadata
    ) {
        Instant now = Instant.now();
        ComplaintEventEntity entity = new ComplaintEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.emailMessageId = emailMessageId;
        entity.eventHash = eventHash;
        entity.correlationStatus = correlationStatus;
        entity.processingStatus = processingStatus;
        entity.provider = clip(provider, 64) == null ? "UNKNOWN" : clip(provider, 64);
        entity.providerEventId = clip(providerEventId, 128);
        entity.complaintType = clip(complaintType, 32) == null ? "UNKNOWN" : clip(complaintType, 32);
        entity.recipient = clip(recipient, 320);
        entity.storedRecipient = clip(storedRecipient, 320);
        entity.originalMessageId = clip(originalMessageId, 255);
        entity.providerMessageId = clip(providerMessageId, 255);
        entity.correlationTokenFp = clip(correlationTokenFp, 8);
        entity.occurredAt = occurredAt;
        entity.receivedAt = now;
        entity.diagnostic = clip(diagnostic, 512);
        entity.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
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

    public String getProcessingStatus() {
        return processingStatus;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getComplaintType() {
        return complaintType;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getStoredRecipient() {
        return storedRecipient;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getCorrelationTokenFp() {
        return correlationTokenFp;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
