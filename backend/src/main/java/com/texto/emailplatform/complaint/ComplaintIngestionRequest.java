package com.texto.emailplatform.complaint;

import java.time.Instant;
import java.util.Map;

/**
 * Provider-neutral trusted complaint input. Tenant is never accepted from this payload.
 * Provider adapters (Gmail/Yahoo/Outlook FBL) belong outside this record.
 */
public record ComplaintIngestionRequest(
        String provider,
        String providerEventId,
        String messageId,
        String providerMessageId,
        String recipient,
        String correlationToken,
        String complaintType,
        Instant occurredAt,
        Map<String, String> metadata
) {
    public static ComplaintIngestionRequest ofToken(String token, String recipient) {
        return new ComplaintIngestionRequest(
                "INTERNAL",
                null,
                null,
                null,
                recipient,
                token,
                "ABUSE",
                Instant.now(),
                Map.of()
        );
    }
}
