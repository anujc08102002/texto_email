package com.texto.emailplatform.bounce;

import java.time.Instant;

/**
 * One RFC 3464 per-recipient DSN block plus classification.
 */
public record DsnRecipient(
        String originalRecipient,
        String finalRecipient,
        String action,
        String status,
        String remoteMta,
        String diagnosticCode,
        String diagnosticMessage,
        Instant lastAttemptDate,
        Instant willRetryUntil,
        BounceClassification classification
) {
}
