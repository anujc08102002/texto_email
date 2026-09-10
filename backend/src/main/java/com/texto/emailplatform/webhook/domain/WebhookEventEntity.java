package com.texto.emailplatform.webhook.domain;

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
@Table(name = "webhook_events")
public class WebhookEventEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DELIVERING = "DELIVERING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_RETRYING = "RETRYING";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "webhook_config_id", nullable = false)
    private UUID webhookConfigId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_response_code")
    private Integer lastResponseCode;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "source_message_id")
    private UUID sourceMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WebhookEventEntity create(
            UUID tenantId,
            UUID webhookConfigId,
            String eventType,
            Map<String, Object> payload,
            UUID sourceMessageId
    ) {
        Instant now = Instant.now();
        WebhookEventEntity entity = new WebhookEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.webhookConfigId = webhookConfigId;
        entity.eventType = eventType;
        entity.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        entity.status = STATUS_PENDING;
        entity.attemptCount = 0;
        entity.sourceMessageId = sourceMessageId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void markDelivering() {
        this.status = STATUS_DELIVERING;
        this.lastAttemptAt = Instant.now();
        this.attemptCount += 1;
        this.updatedAt = Instant.now();
    }

    public void markDelivered(int responseCode) {
        this.status = STATUS_DELIVERED;
        this.lastResponseCode = responseCode;
        this.lastError = null;
        this.deliveredAt = Instant.now();
        this.nextAttemptAt = null;
        this.updatedAt = Instant.now();
    }

    public void markRetrying(int responseCode, String error, Instant nextAttemptAt) {
        this.status = STATUS_RETRYING;
        this.lastResponseCode = responseCode;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = Instant.now();
    }

    public void markFailed(Integer responseCode, String error) {
        this.status = STATUS_FAILED;
        this.lastResponseCode = responseCode;
        this.lastError = truncate(error);
        this.nextAttemptAt = null;
        this.updatedAt = Instant.now();
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getWebhookConfigId() {
        return webhookConfigId;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Integer getLastResponseCode() {
        return lastResponseCode;
    }

    public String getLastError() {
        return lastError;
    }

    public UUID getSourceMessageId() {
        return sourceMessageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
