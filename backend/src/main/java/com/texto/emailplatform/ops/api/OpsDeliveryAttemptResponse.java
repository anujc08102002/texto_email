package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsDeliveryAttemptResponse(
        UUID id,
        UUID tenantId,
        UUID messageId,
        int attemptNumber,
        String status,
        String errorCategory,
        String errorMessage,
        String providerResponse,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
}
