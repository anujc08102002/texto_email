package com.texto.emailplatform.delivery.mta;

/**
 * Structured SMTP transport result. Callers must not parse exception strings.
 */
public record MtaResult(
        MtaOutcome outcome,
        boolean retryable,
        String smtpCode,
        String enhancedStatusCode,
        String diagnostic,
        String providerMessageId
) {

    public static MtaResult success(String smtpCode, String diagnostic, String providerMessageId) {
        return new MtaResult(MtaOutcome.SUCCESS, false, smtpCode, null, sanitize(diagnostic), providerMessageId);
    }

    public static MtaResult failure(
            MtaOutcome outcome,
            boolean retryable,
            String smtpCode,
            String enhancedStatusCode,
            String diagnostic
    ) {
        return new MtaResult(outcome, retryable, smtpCode, enhancedStatusCode, sanitize(diagnostic), null);
    }

    public boolean succeeded() {
        return outcome == MtaOutcome.SUCCESS;
    }

    private static String sanitize(String diagnostic) {
        if (diagnostic == null) {
            return null;
        }
        String trimmed = diagnostic.replaceAll("[\\r\\n]+", " ").trim();
        if (trimmed.length() > 300) {
            return trimmed.substring(0, 300);
        }
        return trimmed;
    }
}
