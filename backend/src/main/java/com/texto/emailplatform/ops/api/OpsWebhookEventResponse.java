package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsWebhookEventResponse(
        UUID id,
        UUID tenantId,
        UUID webhookConfigId,
        UUID sourceMessageId,
        String eventType,
        String status,
        int attemptCount,
        Integer lastResponseCode,
        String lastError,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant deliveredAt,
        Instant createdAt
) {
}
