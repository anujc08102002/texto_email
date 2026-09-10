package com.texto.emailplatform.domain.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Sending domain.
 *
 * <p>{@code status} is authoritative for {@code requireVerifiedSender()}.
 * {@code verificationStatus} is kept identical by {@link #setStatus(String)} so older
 * clients that read either field stay consistent. Do not set them independently.
 */
@Entity
@Table(name = "domains")
public class DomainEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_VERIFYING = "VERIFYING";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "verification_status", nullable = false, length = 32)
    private String verificationStatus;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DomainEntity create(UUID tenantId, String domain) {
        Instant now = Instant.now();
        DomainEntity entity = new DomainEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.domain = domain;
        entity.verificationStatus = STATUS_PENDING;
        entity.status = STATUS_PENDING;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void setStatus(String status) {
        this.status = status;
        this.verificationStatus = status;
        this.updatedAt = Instant.now();
    }

    public void markVerified() {
        setStatus(STATUS_VERIFIED);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getDomain() {
        return domain;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
