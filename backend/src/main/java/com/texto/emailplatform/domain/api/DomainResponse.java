package com.texto.emailplatform.domain.api;

import java.time.Instant;
import java.util.UUID;

public record DomainResponse(
        UUID id,
        String domain,
        String status,
        String verificationStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
