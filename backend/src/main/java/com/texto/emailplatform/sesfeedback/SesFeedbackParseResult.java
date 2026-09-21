package com.texto.emailplatform.sesfeedback;

/**
 * Outcome of parsing an SQS body.
 *
 * <p>{@link Feedback} is a Bounce or Complaint to ingest.
 * {@link Control} is an expected SNS lifecycle message (e.g. SubscriptionConfirmation)
 * that must be deleted so it does not retry forever.
 * {@link Unprocessable} is malformed or unsupported feedback that must not be
 * silently deleted; SQS retry/DLQ handles it.
 */
public sealed interface SesFeedbackParseResult {

    record Feedback(SesFeedbackEvent event) implements SesFeedbackParseResult {
    }

    record Control(String type) implements SesFeedbackParseResult {
    }

    record Unprocessable(String reason) implements SesFeedbackParseResult {
    }
}
