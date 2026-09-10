package com.texto.emailplatform.webhook.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookConfigResponse(
        UUID id,
        String url,
        String description,
        String status,
        String secretPrefix,
        List<String> eventTypes,
        Instant createdAt,
        Instant updatedAt
) {
}
