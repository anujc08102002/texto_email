package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsBounceEventResponse(
        UUID id,
        UUID tenantId,
        UUID emailMessageId,
        String correlationStatus,
        String bounceClass,
        String failureKind,
        String statusCode,
        String diagnosticCode,
        String diagnosticMessage,
        String originalRecipient,
        String finalRecipient,
        String provider,
        String providerEventId,
        Instant receivedAt,
        Instant createdAt
) {
}
