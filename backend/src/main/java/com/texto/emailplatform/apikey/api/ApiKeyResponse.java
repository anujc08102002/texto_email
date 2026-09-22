package com.texto.emailplatform.apikey.api;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;

/**
 * API key metadata. The secret is never included here.
 */
public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        String status,
        String environment,
        Set<String> scopes,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt
) {
}
