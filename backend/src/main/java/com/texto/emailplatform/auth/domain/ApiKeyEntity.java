package com.texto.emailplatform.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "environment", nullable = false, length = 16)
    private String environment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static ApiKeyEntity create(
            UUID tenantId,
            String name,
            String keyPrefix,
            String keyHash,
            String environment,
            Instant expiresAt
    ) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.name = name;
        entity.keyPrefix = keyPrefix;
        entity.keyHash = keyHash;
        entity.status = STATUS_ACTIVE;
        entity.environment = environment;
        entity.createdAt = Instant.now();
        entity.expiresAt = expiresAt;
        return entity;
    }

    public void revoke() {
        this.status = STATUS_REVOKED;
        this.revokedAt = Instant.now();
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status) && revokedAt == null && !isExpired();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getStatus() {
        return status;
    }

    public String getEnvironment() {
        return environment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
