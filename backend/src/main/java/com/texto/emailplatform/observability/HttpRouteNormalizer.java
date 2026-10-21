package com.texto.emailplatform.observability;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class HttpRouteNormalizer {

    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern NUMERIC = Pattern.compile("^\\d+$");

    private HttpRouteNormalizer() {
    }

    public static String normalize(String uri) {
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        String path = uri.split("\\?", 2)[0];
        if (path.startsWith("/api/v1/public/unsubscribe/")) return "/api/v1/public/unsubscribe/{token}";
        String[] parts = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append('/');
            if (UUID_SEGMENT.matcher(part).matches() || NUMERIC.matcher(part).matches()) {
                builder.append("{id}");
            } else {
                builder.append(part);
            }
        }
        String normalized = builder.isEmpty() ? "/" : builder.toString();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    public static UUID extractEmailMessageId(String uri) {
        if (uri == null) {
            return null;
        }
        String path = uri.split("\\?", 2)[0];
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("emails".equals(parts[i]) && UUID_SEGMENT.matcher(parts[i + 1]).matches()) {
                try {
                    return UUID.fromString(parts[i + 1]);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static String operationType(String method, String route) {
        String httpMethod = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        String normalized = route == null ? "" : route;
        if (normalized.startsWith("/api/v1/emails")) {
            if ("POST".equals(httpMethod) && "/api/v1/emails".equals(normalized)) {
                return "EMAIL_SEND";
            }
            if ("GET".equals(httpMethod) && "/api/v1/emails".equals(normalized)) {
                return "EMAIL_LIST";
            }
            if (normalized.startsWith("/api/v1/emails/{id}")) {
                return "EMAIL_" + httpMethod;
            }
            return "EMAIL_" + httpMethod;
        }
        if (normalized.startsWith("/api/v1/admin")) {
            return "OPS_" + httpMethod;
        }
        return httpMethod + "_API";
    }
}
