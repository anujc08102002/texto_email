package com.texto.emailplatform.ops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "platform_admins")
public class PlatformAdminEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PlatformAdminEntity create(String email, String passwordHash, String displayName) {
        Instant now = Instant.now();
        PlatformAdminEntity entity = new PlatformAdminEntity();
        entity.id = UUID.randomUUID();
        entity.email = email.trim().toLowerCase(Locale.ROOT);
        entity.passwordHash = passwordHash;
        entity.displayName = displayName;
        entity.status = STATUS_ACTIVE;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
