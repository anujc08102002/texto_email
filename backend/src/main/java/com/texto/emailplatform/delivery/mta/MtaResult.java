package com.texto.emailplatform.delivery.mta;

public record MtaResult(
        MtaOutcome outcome,
        String smtpCode,
        String response,
        String providerMessageId,
        String errorCategory,
        String errorMessage
) {

    public static MtaResult success(String smtpCode, String response, String providerMessageId) {
        return new MtaResult(MtaOutcome.SUCCESS, smtpCode, response, providerMessageId, null, null);
    }

    public static MtaResult of(
            MtaOutcome outcome,
            String smtpCode,
            String response,
            String errorCategory,
            String errorMessage
    ) {
        return new MtaResult(outcome, smtpCode, response, null, errorCategory, errorMessage);
    }
}
