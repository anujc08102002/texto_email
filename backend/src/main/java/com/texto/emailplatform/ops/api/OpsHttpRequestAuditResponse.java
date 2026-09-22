package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsHttpRequestAuditResponse(
        UUID id,
        String requestId,
        String traceId,
        Instant timestamp,
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
        String operationType,
        Instant createdAt
) {
}
