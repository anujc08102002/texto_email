package com.texto.emailplatform.domain.dkim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dkim_keys")
public class DkimKeyEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String ALGORITHM_RSA = "RSA";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "domain_id", nullable = false)
    private UUID domainId;

    @Column(name = "selector", nullable = false, length = 64)
    private String selector;

    @Column(name = "algorithm", nullable = false, length = 32)
    private String algorithm;

    @Column(name = "key_size", nullable = false)
    private int keySize;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Column(name = "encrypted_private_key", nullable = false)
    private String encryptedPrivateKey;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DkimKeyEntity createActive(
            UUID domainId,
            String selector,
            String publicKey,
            String encryptedPrivateKey,
            int keySize
    ) {
        Instant now = Instant.now();
        DkimKeyEntity entity = new DkimKeyEntity();
        entity.id = UUID.randomUUID();
        entity.domainId = domainId;
        entity.selector = selector;
        entity.algorithm = ALGORITHM_RSA;
        entity.keySize = keySize;
        entity.publicKey = publicKey;
        entity.encryptedPrivateKey = encryptedPrivateKey;
        entity.status = STATUS_ACTIVE;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void retire() {
        this.status = STATUS_RETIRED;
        this.updatedAt = Instant.now();
    }

    public void revoke() {
        this.status = STATUS_REVOKED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDomainId() {
        return domainId;
    }

    public String getSelector() {
        return selector;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getKeySize() {
        return keySize;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getEncryptedPrivateKey() {
        return encryptedPrivateKey;
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

    @Override
    public String toString() {
        return "DkimKeyEntity[id=" + id + ", domainId=" + domainId + ", selector=" + selector + ", status=" + status + "]";
    }
}
