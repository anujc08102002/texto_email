package com.texto.emailplatform.observability;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Redacts credentials and secret-bearing values so HTTP audit rows never persist them.
 * Does not capture request/response bodies or Authorization headers.
 */
public final class HttpRequestAuditSanitizer {

    static final Set<String> FORBIDDEN_HEADER_NAMES = Set.of(
            "authorization",
            "proxy-authorization",
            "x-api-key",
            "x-amz-security-token",
            "x-amz-credential",
            "cookie",
            "set-cookie",
            "authentication-results",
            "received-spf",
            "dkim-signature",
            "arc-seal",
            "arc-authentication-results"
    );

    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)(password|passwd|secret|token|otp|api[_-]?key|authorization|access_key|private[_-]?key|webhook)"
    );
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("(?i)AKIA[0-9A-Z]{16}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern BASIC = Pattern.compile("(?i)basic\\s+\\S+");

    private HttpRequestAuditSanitizer() {
    }

    public static boolean isForbiddenHeader(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String normalized = name.toLowerCase(Locale.ROOT).trim();
        if (FORBIDDEN_HEADER_NAMES.contains(normalized)) {
            return true;
        }
        return normalized.contains("authorization")
                || normalized.contains("api-key")
                || normalized.contains("apikey")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("otp")
                || normalized.contains("private-key")
                || normalized.contains("dkim");
    }

    public static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (containsSecret(trimmed)) {
            return "[redacted]";
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public static String sanitizeClientIp(String forwardedFor, String remoteAddr) {
        if (forwardedFor != null && !forwardedFor.isBlank() && !containsSecret(forwardedFor)) {
            String first = forwardedFor.split(",")[0].trim();
            return clip(first, 64);
        }
        return clip(remoteAddr, 64);
    }

    public static boolean containsSecret(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (SENSITIVE_QUERY.matcher(value).find()) {
            return true;
        }
        if (AWS_ACCESS_KEY.matcher(value).find()) {
            return true;
        }
        if (BEARER.matcher(value).find() || BASIC.matcher(value).find()) {
            return true;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("aws_secret")
                || lower.contains("begin rsa private")
                || lower.contains("begin private key")
                || lower.contains("dkim") && lower.contains("private");
    }

    public static String defaultErrorCategory(int status) {
        if (status >= 500) {
            return "SERVER";
        }
        if (status == 401) {
            return "AUTH";
        }
        if (status == 403) {
            return "AUTHZ";
        }
        if (status == 429) {
            return "RATE_LIMIT";
        }
        if (status >= 400) {
            return "CLIENT";
        }
        return null;
    }

    public static String defaultErrorCode(int status, String existing) {
        if (existing != null && !existing.isBlank() && !containsSecret(existing)) {
            return existing.length() <= 64 ? existing : existing.substring(0, 64);
        }
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 429 -> "RATE_LIMITED";
            default -> status >= 500 ? "INTERNAL_ERROR" : null;
        };
    }
}
