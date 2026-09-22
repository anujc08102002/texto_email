package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsSuppressionResponse(
        UUID id,
        UUID tenantId,
        UUID messageId,
        String email,
        String type,
        String source,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
}
