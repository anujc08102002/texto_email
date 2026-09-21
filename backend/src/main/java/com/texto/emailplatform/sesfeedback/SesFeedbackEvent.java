package com.texto.emailplatform.sesfeedback;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Normalized SES feedback event (bounce or complaint).
 * Small internal model for SQS message processing.
 */
public record SesFeedbackEvent(
        String eventType,
        String messageId,
        List<String> destinations,
        String source,
        Instant timestamp,
        // Bounce fields
        String bounceType,
        String bounceSubType,
        String action,
        String status,
        String diagnosticCode,
        String reportingMta,
        String remoteMta,
        String feedbackId,
        List<BouncedRecipient> bouncedRecipients,
        // Complaint fields
        String complaintFeedbackType,
        List<ComplainedRecipient> complainedRecipients,
        Map<String, String> metadata
) {
    public boolean isBounce() {
        return "Bounce".equals(eventType);
    }

    public boolean isComplaint() {
        return "Complaint".equals(eventType);
    }

    public record BouncedRecipient(
            String emailAddress,
            String action,
            String status,
            String diagnosticCode
    ) {}

    public record ComplainedRecipient(
            String emailAddress
    ) {}
}
