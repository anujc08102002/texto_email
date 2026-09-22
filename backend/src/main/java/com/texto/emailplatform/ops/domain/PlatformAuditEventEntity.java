package com.texto.emailplatform.ops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_audit_events")
public class PlatformAuditEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "method", nullable = false, length = 16)
    private String method;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(name = "query_string", length = 1024)
    private String queryString;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "remote_address", length = 64)
    private String remoteAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PlatformAuditEventEntity create(
            UUID adminId,
            String method,
            String path,
            String queryString,
            Integer statusCode,
            Integer durationMs,
            String requestId,
            String remoteAddress
    ) {
        PlatformAuditEventEntity entity = new PlatformAuditEventEntity();
        entity.id = UUID.randomUUID();
        entity.adminId = adminId;
        entity.method = clip(method, 16);
        entity.path = clip(path, 512);
        entity.queryString = clip(queryString, 1024);
        entity.statusCode = statusCode;
        entity.durationMs = durationMs;
        entity.requestId = clip(requestId, 128);
        entity.remoteAddress = clip(remoteAddress, 64);
        entity.createdAt = Instant.now();
        return entity;
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getQueryString() {
        return queryString;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
