package com.texto.emailplatform.email.api;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptResponse(
        UUID id,
        int attemptNumber,
        String status,
        Instant startedAt,
        Instant completedAt,
        String providerResponse,
        String errorCategory,
        String errorMessage
) {
}
