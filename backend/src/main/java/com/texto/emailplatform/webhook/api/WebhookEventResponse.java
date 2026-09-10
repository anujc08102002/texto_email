package com.texto.emailplatform.webhook.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebhookEventResponse(
        UUID id,
        String eventType,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant deliveredAt,
        Integer lastResponseCode,
        String lastError,
        UUID sourceMessageId,
        Map<String, Object> payload,
        Instant createdAt
) {
}
