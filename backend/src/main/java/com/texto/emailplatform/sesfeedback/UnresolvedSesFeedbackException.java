package com.texto.emailplatform.sesfeedback;

/**
 * SES feedback could not be uniquely correlated to an outbound message.
 * Typical causes: {@code providerMessageId} not persisted yet, or more than one matching row.
 * The SQS consumer must not delete the message so visibility timeout / redrive/DLQ can apply.
 * Distinct from {@link UnprocessableSesFeedbackException} (malformed or unsupported payload).
 */
public class UnresolvedSesFeedbackException extends RuntimeException {

    public UnresolvedSesFeedbackException(String reason) {
        super(reason);
    }
}
