package com.texto.emailplatform.bounce;

import com.texto.emailplatform.bounce.domain.BounceEventEntity;
import java.util.List;

public record DsnIngestionResult(
        Outcome outcome,
        String failureReason,
        List<BounceEventEntity> events
) {
    public enum Outcome {
        ACCEPTED,
        DUPLICATE,
        PARSE_FAILED,
        REJECTED,
        UNMATCHED
    }

    public static DsnIngestionResult accepted(List<BounceEventEntity> events) {
        return new DsnIngestionResult(Outcome.ACCEPTED, null, List.copyOf(events));
    }

    public static DsnIngestionResult duplicate(List<BounceEventEntity> events) {
        return new DsnIngestionResult(Outcome.DUPLICATE, null, List.copyOf(events));
    }

    public static DsnIngestionResult parseFailed(String reason, List<BounceEventEntity> events) {
        return new DsnIngestionResult(Outcome.PARSE_FAILED, reason, List.copyOf(events));
    }

    public static DsnIngestionResult unmatched(List<BounceEventEntity> events) {
        return new DsnIngestionResult(Outcome.UNMATCHED, "unmatched", List.copyOf(events));
    }

    public static DsnIngestionResult rejected(String reason) {
        return new DsnIngestionResult(Outcome.REJECTED, reason, List.of());
    }
}
