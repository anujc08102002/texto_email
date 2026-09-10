package com.texto.emailplatform.billing.domain;

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
@Table(name = "billing_events")
public class BillingEventEntity {

    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_IGNORED = "IGNORED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static BillingEventEntity received(
            String provider,
            String providerEventId,
            String eventType,
            Map<String, Object> payload
    ) {
        BillingEventEntity entity = new BillingEventEntity();
        entity.id = UUID.randomUUID();
        entity.provider = provider;
        entity.providerEventId = providerEventId;
        entity.eventType = eventType;
        entity.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        entity.processingStatus = STATUS_RECEIVED;
        entity.createdAt = Instant.now();
        return entity;
    }

    public void link(UUID tenantId, UUID subscriptionId) {
        this.tenantId = tenantId;
        this.subscriptionId = subscriptionId;
    }

    public void markProcessed() {
        this.processingStatus = STATUS_PROCESSED;
        this.processedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markIgnored(String reason) {
        this.processingStatus = STATUS_IGNORED;
        this.processedAt = Instant.now();
        this.errorMessage = truncate(reason);
    }

    public void markFailed(String error) {
        this.processingStatus = STATUS_FAILED;
        this.processedAt = Instant.now();
        this.errorMessage = truncate(error);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
