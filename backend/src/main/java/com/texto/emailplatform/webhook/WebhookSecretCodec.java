package com.texto.emailplatform.webhook;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Local reversible protection for webhook signing secrets.
 * Production should replace this with KMS/secret-manager backed storage.
 */
public final class WebhookSecretCodec {

    private static final String PREFIX = "enc:";

    private WebhookSecretCodec() {
    }

    public static String protect(String secret) {
        return PREFIX + Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static String reveal(String protectedValue) {
        if (protectedValue == null || !protectedValue.startsWith(PREFIX)) {
            throw new IllegalStateException("Webhook secret material is unavailable");
        }
        byte[] decoded = Base64.getDecoder().decode(protectedValue.substring(PREFIX.length()));
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
