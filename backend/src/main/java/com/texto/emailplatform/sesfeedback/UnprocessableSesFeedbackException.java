package com.texto.emailplatform.sesfeedback;

/**
 * Malformed or unsupported SES/SNS payload that is not an expected control message.
 * The SQS consumer must not delete the message so the queue redrive/DLQ can take it.
 */
public class UnprocessableSesFeedbackException extends RuntimeException {

    public UnprocessableSesFeedbackException(String reason) {
        super(reason);
    }
}
