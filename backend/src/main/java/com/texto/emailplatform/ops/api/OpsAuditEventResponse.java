package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsAuditEventResponse(
        UUID id,
        UUID adminId,
        String method,
        String path,
        String queryString,
        Integer statusCode,
        Integer durationMs,
        String requestId,
        Instant createdAt
) {
}
