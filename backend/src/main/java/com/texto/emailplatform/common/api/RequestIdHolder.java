package com.texto.emailplatform.common.api;

import org.slf4j.MDC;

public final class RequestIdHolder {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIdHolder() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
