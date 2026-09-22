package com.texto.emailplatform.ops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_admin_sessions")
public class PlatformAdminSessionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PlatformAdminSessionEntity create(UUID adminId, String tokenHash, Instant expiresAt) {
        PlatformAdminSessionEntity entity = new PlatformAdminSessionEntity();
        entity.id = UUID.randomUUID();
        entity.adminId = adminId;
        entity.tokenHash = tokenHash;
        entity.expiresAt = expiresAt;
        entity.createdAt = Instant.now();
        return entity;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getTokenHash() {
        return tokenHash;
    }
}
