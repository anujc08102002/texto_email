package com.texto.emailplatform.common.observability;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class RequestCorrelation {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String REQUEST_ID_MDC = "requestId";
    public static final String TRACE_ID_MDC = "traceId";

    public static final String PAYLOAD_REQUEST_ID = "requestId";
    public static final String PAYLOAD_TRACE_ID = "traceId";

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^[0-9a-fA-F]{2}-([0-9a-fA-F]{32})-[0-9a-fA-F]{16}-[0-9a-fA-F]{2}$"
    );

    private RequestCorrelation() {
    }

    public static void bind(String requestId, String traceId) {
        String safeRequestId = clip(requestId);
        String safeTraceId = clip(traceId);
        if (safeRequestId != null) {
            MDC.put(REQUEST_ID_MDC, safeRequestId);
        }
        if (safeTraceId != null) {
            MDC.put(TRACE_ID_MDC, safeTraceId);
        } else if (safeRequestId != null) {
            MDC.put(TRACE_ID_MDC, safeRequestId);
        }
    }

    public static void clear() {
        MDC.remove(REQUEST_ID_MDC);
        MDC.remove(TRACE_ID_MDC);
    }

    public static String requestId() {
        return clip(MDC.get(REQUEST_ID_MDC));
    }

    public static String traceId() {
        return clip(MDC.get(TRACE_ID_MDC));
    }

    public static String requestIdOrUnknown() {
        String value = requestId();
        return value == null ? "unknown" : value;
    }

    public static String resolveRequestId(String incoming) {
        String clipped = clip(incoming);
        return clipped == null ? UUID.randomUUID().toString() : clipped;
    }

    public static String resolveTraceId(String incomingTraceId, String traceparent, String requestId) {
        String fromHeader = clip(incomingTraceId);
        if (fromHeader != null) {
            return fromHeader;
        }
        String fromTraceparent = parseTraceparent(traceparent);
        if (fromTraceparent != null) {
            return fromTraceparent;
        }
        return clip(requestId);
    }

    public static String parseTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        Matcher matcher = TRACEPARENT.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    public static void putInto(Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        String requestId = requestId();
        String traceId = traceId();
        if (requestId != null) {
            payload.put(PAYLOAD_REQUEST_ID, requestId);
        }
        if (traceId != null) {
            payload.put(PAYLOAD_TRACE_ID, traceId);
        }
    }

    public static String fromPayload(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) {
            return null;
        }
        return clip(String.valueOf(payload.get(key)));
    }

    public static String clip(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 128) {
            return null;
        }
        return trimmed;
    }
}
