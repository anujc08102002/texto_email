package com.texto.emailplatform.suppression.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppressions")
public class SuppressionEntity {

    public static final String TYPE_BOUNCE = "BOUNCE";
    public static final String TYPE_COMPLAINT = "COMPLAINT";
    public static final String TYPE_UNSUBSCRIBE = "UNSUBSCRIBE";
    public static final String TYPE_MANUAL = "MANUAL";

    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_IMPORT = "IMPORT";
    public static final String SOURCE_WEBHOOK = "WEBHOOK";
    public static final String SOURCE_CAMPAIGN = "CAMPAIGN";
    public static final String SOURCE_API = "API";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 320)
    private String normalizedEmail;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SuppressionEntity create(
            UUID tenantId,
            String email,
            String normalizedEmail,
            String reason,
            String type,
            String source,
            UUID messageId
    ) {
        Instant now = Instant.now();
        SuppressionEntity entity = new SuppressionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.email = email;
        entity.normalizedEmail = normalizedEmail;
        entity.reason = reason;
        entity.type = type;
        entity.source = source;
        entity.messageId = messageId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getReason() {
        return reason;
    }

    public String getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
