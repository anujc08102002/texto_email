package com.texto.emailplatform.bounce;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Opaque 128-bit bounce correlation token. Not derived from tenant, email, or recipient.
 */
public final class BounceCorrelationToken {

    public static final int HEX_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern HEX_32 = Pattern.compile("^[0-9a-f]{" + HEX_LENGTH + "}$");

    private BounceCorrelationToken() {
    }

    public static String generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static boolean isWellFormed(String token) {
        return token != null && HEX_32.matcher(token).matches();
    }

    public static String normalize(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        return isWellFormed(trimmed) ? trimmed : null;
    }

    /** Truncated SHA-256 for logs. Never log the raw token. */
    public static String fingerprint(String token) {
        if (token == null || token.isBlank()) {
            return "none";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
