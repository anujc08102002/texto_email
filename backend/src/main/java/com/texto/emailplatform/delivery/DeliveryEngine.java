package com.texto.emailplatform.delivery;

import java.util.List;
import java.util.UUID;

/**
 * Application-facing delivery port. Implementations compose MIME, DKIM-sign, then
 * submit through {@link com.texto.emailplatform.delivery.mta.MtaClient}.
 */
public interface DeliveryEngine {

    DeliveryResult deliver(DeliveryRequest request);

    record DeliveryRequest(
            String from,
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String replyTo,
            String subject,
            String textBody,
            String htmlBody,
            UUID messageId,
            UUID tenantId
    ) {
        public DeliveryRequest(
                String from,
                List<String> to,
                List<String> cc,
                List<String> bcc,
                String replyTo,
                String subject,
                String textBody,
                String htmlBody
        ) {
            this(from, to, cc, bcc, replyTo, subject, textBody, htmlBody, null, null);
        }
    }

    enum Outcome {
        SUCCESS,
        TEMPORARY_FAILURE,
        PERMANENT_FAILURE
    }

    record DeliveryResult(
            Outcome outcome,
            boolean retryable,
            String providerMessageId,
            String smtpCode,
            String providerResponse,
            String errorCategory,
            String errorMessage
    ) {
        public static DeliveryResult success(String providerMessageId, String response) {
            return new DeliveryResult(Outcome.SUCCESS, false, providerMessageId, "250", response, null, null);
        }

        public static DeliveryResult temporary(String category, String message, String smtpCode) {
            return new DeliveryResult(Outcome.TEMPORARY_FAILURE, true, null, smtpCode, message, category, message);
        }

        public static DeliveryResult permanent(String category, String message, String smtpCode) {
            return new DeliveryResult(Outcome.PERMANENT_FAILURE, false, null, smtpCode, message, category, message);
        }
    }
}
