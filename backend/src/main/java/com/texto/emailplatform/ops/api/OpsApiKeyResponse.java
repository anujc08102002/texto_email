package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsApiKeyResponse(
        UUID id,
        UUID tenantId,
        String name,
        String keyPrefix,
        String status,
        String environment,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt
) {
}
