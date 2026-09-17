package com.texto.emailplatform.bounce;

import java.time.Instant;
import java.util.List;

/**
 * Structured RFC 3464 DSN. Does not include the raw MIME as a domain field.
 */
public record ParsedDsn(
        boolean success,
        String failureReason,
        String reportingMta,
        Instant arrivalDate,
        String originalEnvelopeId,
        String originalMessageId,
        String originalSender,
        String reportTo,
        List<DsnRecipient> recipients
) {
    public static ParsedDsn failure(String reason) {
        return new ParsedDsn(false, reason, null, null, null, null, null, null, List.of());
    }

    public static ParsedDsn success(
            String reportingMta,
            Instant arrivalDate,
            String originalEnvelopeId,
            String originalMessageId,
            String originalSender,
            String reportTo,
            List<DsnRecipient> recipients
    ) {
        return new ParsedDsn(
                true,
                null,
                reportingMta,
                arrivalDate,
                originalEnvelopeId,
                originalMessageId,
                originalSender,
                reportTo,
                recipients == null ? List.of() : List.copyOf(recipients)
        );
    }
}
