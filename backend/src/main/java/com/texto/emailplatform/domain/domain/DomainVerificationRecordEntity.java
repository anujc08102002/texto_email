package com.texto.emailplatform.domain.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "domain_verification_records")
public class DomainVerificationRecordEntity {

    public static final String TYPE_SPF = "SPF";
    public static final String TYPE_DKIM = "DKIM";
    public static final String TYPE_DMARC = "DMARC";
    public static final String TYPE_OWNERSHIP = "OWNERSHIP";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_MISSING = "MISSING";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "domain_id", nullable = false)
    private UUID domainId;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "selector", length = 64)
    private String selector;

    @Column(name = "public_key")
    private String publicKey;

    @Column(name = "private_key_ref")
    private String privateKeyRef;

    @Column(name = "last_error", length = 64)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public static DomainVerificationRecordEntity create(
            UUID domainId,
            String type,
            String name,
            String value,
            String selector,
            String publicKey,
            String privateKeyRef
    ) {
        DomainVerificationRecordEntity entity = new DomainVerificationRecordEntity();
        entity.id = UUID.randomUUID();
        entity.domainId = domainId;
        entity.type = type;
        entity.name = name;
        entity.value = value;
        entity.status = STATUS_PENDING;
        entity.selector = selector;
        entity.publicKey = publicKey;
        entity.privateKeyRef = privateKeyRef;
        entity.createdAt = Instant.now();
        return entity;
    }

    public void markVerified() {
        this.status = STATUS_VERIFIED;
        this.verifiedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed() {
        this.status = STATUS_FAILED;
        this.verifiedAt = null;
    }

    public void markFailed(String errorCode) {
        markFailed();
        this.lastError = errorCode;
    }

    public void markMissing() {
        this.status = STATUS_MISSING;
        this.verifiedAt = null;
    }

    public void markMissing(String errorCode) {
        markMissing();
        this.lastError = errorCode;
    }

    public void replaceDkimMaterial(String dnsValue, String publicKey, String privateKeyRef) {
        this.value = dnsValue;
        this.publicKey = publicKey;
        this.privateKeyRef = privateKeyRef;
    }

    public void assignKeyRef(String keyRef) {
        this.privateKeyRef = keyRef;
    }

    public void syncDkimDns(String dnsValue, String publicKey, String selector, String keyRef) {
        this.value = dnsValue;
        this.publicKey = publicKey;
        if (selector != null && !selector.isBlank()) {
            this.selector = selector;
        }
        this.privateKeyRef = keyRef;
    }

    public void replaceValue(String value) {
        this.value = value;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void clearLastError() {
        this.lastError = null;
    }

    public String getLastError() {
        return lastError;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDomainId() {
        return domainId;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getStatus() {
        return status;
    }

    public String getSelector() {
        return selector;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKeyRef() {
        return privateKeyRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
