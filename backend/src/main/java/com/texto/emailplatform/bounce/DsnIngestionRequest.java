package com.texto.emailplatform.bounce;

/**
 * Trusted infrastructure envelope for a raw DSN. Tenant is never taken from DSN fields
 * or from a caller-supplied tenant id; it is resolved from the bounce token in the database.
 */
public record DsnIngestionRequest(byte[] rawRfc822, String envelopeRecipient) {

    public DsnIngestionRequest {
        rawRfc822 = rawRfc822 == null ? new byte[0] : rawRfc822.clone();
    }

    public static DsnIngestionRequest of(byte[] rawRfc822) {
        return new DsnIngestionRequest(rawRfc822, null);
    }
}
