package com.texto.emailplatform.suppression.api;

import java.time.Instant;
import java.util.UUID;

public record SuppressionResponse(
        UUID id,
        String email,
        String type,
        String reason,
        String source,
        UUID messageId,
        Instant createdAt
) {
}
