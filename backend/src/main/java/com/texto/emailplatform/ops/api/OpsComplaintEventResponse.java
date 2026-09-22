package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsComplaintEventResponse(
        UUID id,
        UUID tenantId,
        UUID emailMessageId,
        String correlationStatus,
        String processingStatus,
        String provider,
        String providerEventId,
        String complaintType,
        String recipient,
        String originalMessageId,
        String providerMessageId,
        String diagnostic,
        Instant occurredAt,
        Instant receivedAt,
        Instant createdAt
) {
}
