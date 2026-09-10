package com.texto.emailplatform.delivery;

import java.util.List;
import java.util.UUID;

/**
 * Abstraction over outbound email providers. Mailpit for local; Postfix later.
 */
public interface DeliveryEngine {

    DeliveryResult deliver(DeliveryRequest request);

    record DeliveryRequest(
            UUID tenantId,
            String from,
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String replyTo,
            String subject,
            String textBody,
            String htmlBody
    ) {
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
