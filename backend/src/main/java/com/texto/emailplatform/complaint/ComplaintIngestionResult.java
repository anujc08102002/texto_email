package com.texto.emailplatform.complaint;

import com.texto.emailplatform.complaint.domain.ComplaintEventEntity;

public record ComplaintIngestionResult(
        Outcome outcome,
        String failureReason,
        ComplaintEventEntity event
) {
    public enum Outcome {
        ACCEPTED,
        DUPLICATE,
        UNCORRELATED,
        PARSE_FAILED,
        REJECTED
    }

    public static ComplaintIngestionResult accepted(ComplaintEventEntity event) {
        return new ComplaintIngestionResult(Outcome.ACCEPTED, null, event);
    }

    public static ComplaintIngestionResult duplicate(ComplaintEventEntity event) {
        return new ComplaintIngestionResult(Outcome.DUPLICATE, null, event);
    }

    public static ComplaintIngestionResult uncorrelated(ComplaintEventEntity event) {
        return new ComplaintIngestionResult(Outcome.UNCORRELATED, "unmatched", event);
    }

    public static ComplaintIngestionResult parseFailed(String reason, ComplaintEventEntity event) {
        return new ComplaintIngestionResult(Outcome.PARSE_FAILED, reason, event);
    }

    public static ComplaintIngestionResult rejected(String reason) {
        return new ComplaintIngestionResult(Outcome.REJECTED, reason, null);
    }
}
