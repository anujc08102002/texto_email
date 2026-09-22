package com.texto.emailplatform.observability;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpRequestAuditAttributes {

    public static final String ERROR_CODE = "texto.http.audit.errorCode";
    public static final String ERROR_CATEGORY = "texto.http.audit.errorCategory";
    public static final String EMAIL_MESSAGE_ID = "texto.http.audit.emailMessageId";
    public static final String PROVIDER_MESSAGE_ID = "texto.http.audit.providerMessageId";
    public static final String OPERATION_TYPE = "texto.http.audit.operationType";

    private HttpRequestAuditAttributes() {
    }

    public static void recordError(HttpServletRequest request, String code, String category) {
        if (request == null) {
            return;
        }
        if (code != null && !code.isBlank()) {
            request.setAttribute(ERROR_CODE, code);
        }
        if (category != null && !category.isBlank()) {
            request.setAttribute(ERROR_CATEGORY, category);
        }
    }

    public static void recordEmailOperation(
            HttpServletRequest request,
            java.util.UUID messageId,
            String providerMessageId,
            String operationType
    ) {
        if (request == null) {
            return;
        }
        if (messageId != null) {
            request.setAttribute(EMAIL_MESSAGE_ID, messageId);
        }
        if (providerMessageId != null && !providerMessageId.isBlank()) {
            request.setAttribute(PROVIDER_MESSAGE_ID, providerMessageId);
        }
        if (operationType != null && !operationType.isBlank()) {
            request.setAttribute(OPERATION_TYPE, operationType);
        }
    }

    public static String errorCode(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(ERROR_CODE);
        return value == null ? null : value.toString();
    }

    public static String errorCategory(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(ERROR_CATEGORY);
        return value == null ? null : value.toString();
    }

    public static java.util.UUID emailMessageId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(EMAIL_MESSAGE_ID);
        if (value instanceof java.util.UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String providerMessageId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(PROVIDER_MESSAGE_ID);
        return value == null ? null : value.toString();
    }

    public static String operationType(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(OPERATION_TYPE);
        return value == null ? null : value.toString();
    }
}
