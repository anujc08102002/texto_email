package com.texto.emailplatform.email.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttemptEntity {

    public static final String STARTED = "STARTED";
    public static final String SUCCESS = "SUCCESS";
    public static final String TEMPORARY_FAILURE = "TEMPORARY_FAILURE";
    public static final String PERMANENT_FAILURE = "PERMANENT_FAILURE";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "provider_response")
    private String providerResponse;

    @Column(name = "error_category", length = 64)
    private String errorCategory;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static DeliveryAttemptEntity start(UUID messageId, UUID tenantId, int attemptNumber) {
        Instant now = Instant.now();
        DeliveryAttemptEntity entity = new DeliveryAttemptEntity();
        entity.id = UUID.randomUUID();
        entity.messageId = messageId;
        entity.tenantId = tenantId;
        entity.attemptNumber = attemptNumber;
        entity.status = STARTED;
        entity.startedAt = now;
        entity.createdAt = now;
        return entity;
    }

    public void completeSuccess(String providerResponse) {
        this.status = SUCCESS;
        this.providerResponse = truncate(providerResponse);
        this.completedAt = Instant.now();
    }

    public void completeTemporary(String category, String message, String providerResponse) {
        this.status = TEMPORARY_FAILURE;
        this.errorCategory = category;
        this.errorMessage = truncate(message);
        this.providerResponse = truncate(providerResponse);
        this.completedAt = Instant.now();
    }

    public void completePermanent(String category, String message, String providerResponse) {
        this.status = PERMANENT_FAILURE;
        this.errorCategory = category;
        this.errorMessage = truncate(message);
        this.providerResponse = truncate(providerResponse);
        this.completedAt = Instant.now();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getProviderResponse() {
        return providerResponse;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
