package com.texto.emailplatform.bounce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * SHA-256 idempotency key over canonical DSN fields plus tenant. Not a timestamp.
 */
public final class BounceEventHash {

    private BounceEventHash() {
    }

    public static String canonical(
            UUID tenantId,
            String originalMessageId,
            String finalRecipient,
            String action,
            String status,
            String diagnosticCode,
            String reportingMta,
            String arrivalDate
    ) {
        String material = String.join(
                "|",
                tenantId == null ? "" : tenantId.toString(),
                normalize(originalMessageId),
                normalizeAddress(finalRecipient),
                normalize(action),
                normalize(status),
                normalize(diagnosticCode),
                normalize(reportingMta),
                normalize(arrivalDate)
        );
        return sha256(material);
    }

    public static String parseFailure(UUID tenantId, byte[] rawRfc822) {
        String rawHash = sha256(rawRfc822 == null ? new byte[0] : rawRfc822);
        String material = (tenantId == null ? "" : tenantId) + "|PARSE_FAILED|" + rawHash;
        return sha256(material);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeAddress(String value) {
        return normalize(value).replace("<", "").replace(">", "");
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
