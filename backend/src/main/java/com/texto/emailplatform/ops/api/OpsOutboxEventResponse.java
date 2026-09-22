package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsOutboxEventResponse(
        UUID id,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant publishedAt
) {
}
