package com.texto.emailplatform.bounce;

/**
 * Platform bounce envelope address. Not a MIME From header.
 * {@code bounce+<token>@<bounce-domain>}
 */
public final class BounceAddress {

    public static final String LOCAL_PART_PREFIX = "bounce+";

    private BounceAddress() {
    }

    public static String format(String token, String domain) {
        String normalized = BounceCorrelationToken.normalize(token);
        String host = normalizeDomain(domain);
        if (normalized == null || host == null) {
            throw new IllegalArgumentException("bounce address requires a well-formed token and domain");
        }
        return LOCAL_PART_PREFIX + normalized + "@" + host;
    }

    public static String mailFromOrFallback(String token, String domain, String fallbackFrom) {
        String normalized = BounceCorrelationToken.normalize(token);
        String host = normalizeDomain(domain);
        if (normalized == null || host == null) {
            return fallbackFrom;
        }
        return LOCAL_PART_PREFIX + normalized + "@" + host;
    }

    static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String trimmed = domain.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.startsWith(".") || trimmed.endsWith(".") || !trimmed.contains(".")) {
            return null;
        }
        return trimmed;
    }
}
