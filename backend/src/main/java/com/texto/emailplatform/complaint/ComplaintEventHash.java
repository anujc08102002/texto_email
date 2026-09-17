package com.texto.emailplatform.complaint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * SHA-256 idempotency key. Prefers provider event ID. Never uses received_at.
 */
public final class ComplaintEventHash {

    private ComplaintEventHash() {
    }

    public static String providerEvent(String provider, String providerEventId) {
        String material = String.join("|", "PEID", normalize(provider), normalize(providerEventId));
        return sha256(material);
    }

    public static String canonical(
            String provider,
            String messageId,
            String providerMessageId,
            String recipient,
            String complaintType,
            String correlationToken
    ) {
        String material = String.join(
                "|",
                "CANON",
                normalize(provider),
                normalize(messageId),
                normalize(providerMessageId),
                normalizeAddress(recipient),
                normalize(complaintType),
                normalize(correlationToken)
        );
        return sha256(material);
    }

    public static String parseFailure(byte[] raw) {
        String rawHash = sha256(raw == null ? new byte[0] : raw);
        return sha256("PARSE_FAILED|" + rawHash);
    }

    public static String identity(ComplaintIngestionRequest request) {
        if (request == null) {
            return parseFailure(new byte[0]);
        }
        if (request.providerEventId() != null && !request.providerEventId().isBlank()) {
            return providerEvent(request.provider(), request.providerEventId());
        }
        return canonical(
                request.provider(),
                request.messageId(),
                request.providerMessageId(),
                request.recipient(),
                request.complaintType(),
                request.correlationToken()
        );
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
