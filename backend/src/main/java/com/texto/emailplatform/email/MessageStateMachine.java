package com.texto.emailplatform.email;

import com.texto.emailplatform.common.exception.ApiException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Central authority for email message status transitions.
 */
public final class MessageStateMachine {

    public static final String QUEUED = "QUEUED";
    public static final String PROCESSING = "PROCESSING";
    public static final String SENDING = "SENDING";
    public static final String DELIVERED = "DELIVERED";
    public static final String DEFERRED = "DEFERRED";
    public static final String FAILED = "FAILED";
    public static final String BOUNCED = "BOUNCED";
    public static final String SUPPRESSED = "SUPPRESSED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry(QUEUED, Set.of(PROCESSING, SUPPRESSED, CANCELLED, EXPIRED)),
            Map.entry(PROCESSING, Set.of(SENDING, FAILED, CANCELLED)),
            Map.entry(SENDING, Set.of(DELIVERED, DEFERRED, FAILED, BOUNCED)),
            Map.entry(DEFERRED, Set.of(QUEUED, PROCESSING, FAILED, EXPIRED, BOUNCED)),
            Map.entry(DELIVERED, Set.of(BOUNCED)),
            Map.entry(FAILED, Set.of()),
            Map.entry(BOUNCED, Set.of()),
            Map.entry(SUPPRESSED, Set.of()),
            Map.entry(CANCELLED, Set.of()),
            Map.entry(EXPIRED, Set.of())
    );

    private MessageStateMachine() {
    }

    public static boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(String from, String to) {
        Set<String> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "INVALID_STATE_TRANSITION",
                    "Cannot transition message from " + from + " to " + to
            );
        }
    }

    public static boolean isTerminal(String status) {
        return Set.of(DELIVERED, FAILED, BOUNCED, SUPPRESSED, CANCELLED, EXPIRED).contains(status);
    }
}
