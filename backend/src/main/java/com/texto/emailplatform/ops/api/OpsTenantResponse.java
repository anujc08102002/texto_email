package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsTenantResponse(
        UUID id,
        String name,
        String slug,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
