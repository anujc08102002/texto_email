package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsUserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String displayName,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
