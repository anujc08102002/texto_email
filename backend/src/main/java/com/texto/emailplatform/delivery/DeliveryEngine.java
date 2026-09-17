package com.texto.emailplatform.delivery;

import java.util.List;

/**
 * Abstraction over outbound email delivery. {@link SmtpDeliveryEngine} composes MIME and DKIM,
 * then submits via {@code MtaClient} (Mailpit or Postfix).
 */
public interface DeliveryEngine {

    DeliveryResult deliver(DeliveryRequest request);

    record DeliveryRequest(
            java.util.UUID tenantId,
            String from,
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String replyTo,
            String subject,
            String textBody,
            String htmlBody,
            String bounceCorrelationToken
    ) {
        public DeliveryRequest(
                java.util.UUID tenantId,
                String from,
                List<String> to,
                List<String> cc,
                List<String> bcc,
                String replyTo,
                String subject,
                String textBody,
                String htmlBody
        ) {
            this(tenantId, from, to, cc, bcc, replyTo, subject, textBody, htmlBody, null);
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
            String errorMessage,
            String rfc822MessageId
    ) {
        public static DeliveryResult success(String providerMessageId, String response) {
            return new DeliveryResult(Outcome.SUCCESS, false, providerMessageId, "250", response, null, null, null);
        }

        public static DeliveryResult temporary(String category, String message, String smtpCode) {
            return new DeliveryResult(Outcome.TEMPORARY_FAILURE, true, null, smtpCode, message, category, message, null);
        }

        public static DeliveryResult permanent(String category, String message, String smtpCode) {
            return new DeliveryResult(Outcome.PERMANENT_FAILURE, false, null, smtpCode, message, category, message, null);
        }

        public DeliveryResult withRfc822MessageId(String messageId) {
            return new DeliveryResult(
                    outcome,
                    retryable,
                    providerMessageId,
                    smtpCode,
                    providerResponse,
                    errorCategory,
                    errorMessage,
                    messageId
            );
        }
    }
}
