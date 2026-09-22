package com.texto.emailplatform.observability.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "http_request_audit")
public class HttpRequestAuditEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "principal_id")
    private UUID principalId;

    @Column(name = "api_key_id")
    private UUID apiKeyId;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "route", nullable = false, length = 255)
    private String route;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "request_size_bytes")
    private Long requestSizeBytes;

    @Column(name = "response_size_bytes")
    private Long responseSizeBytes;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "referer", length = 512)
    private String referer;

    @Column(name = "auth_outcome", nullable = false, length = 32)
    private String authOutcome;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_category", length = 64)
    private String errorCategory;

    @Column(name = "email_message_id")
    private UUID emailMessageId;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "operation_type", length = 64)
    private String operationType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static HttpRequestAuditEntity create(
            String requestId,
            String traceId,
            Instant occurredAt,
            UUID tenantId,
            UUID principalId,
            UUID apiKeyId,
            String httpMethod,
            String route,
            int statusCode,
            int durationMs,
            Long requestSizeBytes,
            Long responseSizeBytes,
            String clientIp,
            String userAgent,
            String referer,
            String authOutcome,
            String errorCode,
            String errorCategory,
            UUID emailMessageId,
            String providerMessageId,
            String operationType
    ) {
        Instant now = Instant.now();
        HttpRequestAuditEntity entity = new HttpRequestAuditEntity();
        entity.id = UUID.randomUUID();
        entity.requestId = requestId;
        entity.traceId = traceId;
        entity.occurredAt = occurredAt == null ? now : occurredAt;
        entity.tenantId = tenantId;
        entity.principalId = principalId;
        entity.apiKeyId = apiKeyId;
        entity.httpMethod = httpMethod;
        entity.route = route;
        entity.statusCode = statusCode;
        entity.durationMs = durationMs;
        entity.requestSizeBytes = requestSizeBytes;
        entity.responseSizeBytes = responseSizeBytes;
        entity.clientIp = clientIp;
        entity.userAgent = userAgent;
        entity.referer = referer;
        entity.authOutcome = authOutcome;
        entity.errorCode = errorCode;
        entity.errorCategory = errorCategory;
        entity.emailMessageId = emailMessageId;
        entity.providerMessageId = providerMessageId;
        entity.operationType = operationType;
        entity.createdAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPrincipalId() {
        return principalId;
    }

    public UUID getApiKeyId() {
        return apiKeyId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRoute() {
        return route;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public Long getRequestSizeBytes() {
        return requestSizeBytes;
    }

    public Long getResponseSizeBytes() {
        return responseSizeBytes;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public String getAuthOutcome() {
        return authOutcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public UUID getEmailMessageId() {
        return emailMessageId;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getOperationType() {
        return operationType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
