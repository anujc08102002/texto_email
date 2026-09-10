package com.texto.emailplatform.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_configs")
public class WebhookConfigEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "secret_prefix", nullable = false, length = 16)
    private String secretPrefix;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_types", nullable = false, columnDefinition = "jsonb")
    private List<String> eventTypes = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WebhookConfigEntity create(
            UUID tenantId,
            String url,
            String description,
            String secretPrefix,
            String secretHash,
            List<String> eventTypes
    ) {
        Instant now = Instant.now();
        WebhookConfigEntity entity = new WebhookConfigEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.url = url;
        entity.description = description;
        entity.status = STATUS_ACTIVE;
        entity.secretPrefix = secretPrefix;
        entity.secretHash = secretHash;
        entity.eventTypes = eventTypes == null ? new ArrayList<>() : new ArrayList<>(eventTypes);
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void patch(String url, String description, List<String> eventTypes) {
        if (url != null) {
            this.url = url;
        }
        if (description != null) {
            this.description = description;
        }
        if (eventTypes != null) {
            this.eventTypes = new ArrayList<>(eventTypes);
        }
        this.updatedAt = Instant.now();
    }

    public void pause() {
        this.status = STATUS_PAUSED;
        this.updatedAt = Instant.now();
    }

    public void resume() {
        this.status = STATUS_ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.status = STATUS_DISABLED;
        this.updatedAt = Instant.now();
    }

    public void rotateSecret(String secretPrefix, String secretHash) {
        this.secretPrefix = secretPrefix;
        this.secretHash = secretHash;
        this.updatedAt = Instant.now();
    }

    public boolean subscribesTo(String eventType) {
        return eventTypes != null && eventTypes.contains(eventType);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public List<String> getEventTypes() {
        return eventTypes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
