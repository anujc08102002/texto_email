package com.texto.emailplatform.webhook;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Protects webhook signing secrets at rest using AES-GCM.
 * Configure {@code email-platform.webhooks.encryption-key} (Base64 32-byte key) in production.
 * Falls back to a process-local derived key for development only.
 */
@Component
public class WebhookSecretProtector {

    private static final String AES_PREFIX = "aes:";
    private static final String LEGACY_PREFIX = "enc:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey secretKey;

    public WebhookSecretProtector(EmailPlatformProperties properties) {
        this.secretKey = resolveKey(properties.getWebhooks().getEncryptionKey());
    }

    public String protect(String secret) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return AES_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect webhook secret", exception);
        }
    }

    public String reveal(String protectedValue) {
        if (protectedValue == null) {
            throw new IllegalStateException("Webhook secret material is unavailable");
        }
        if (protectedValue.startsWith(LEGACY_PREFIX)) {
            byte[] decoded = Base64.getDecoder().decode(protectedValue.substring(LEGACY_PREFIX.length()));
            return new String(decoded, StandardCharsets.UTF_8);
        }
        if (!protectedValue.startsWith(AES_PREFIX)) {
            throw new IllegalStateException("Webhook secret material is unavailable");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(protectedValue.substring(AES_PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(raw, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(raw, GCM_IV_LENGTH, raw.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to reveal webhook secret", exception);
        }
    }

    private static SecretKey resolveKey(String configured) {
        try {
            if (configured != null && !configured.isBlank()) {
                byte[] keyBytes = Base64.getDecoder().decode(configured.trim());
                if (keyBytes.length != 32) {
                    throw new IllegalStateException("Webhook encryption key must be 32 bytes (Base64-encoded)");
                }
                return new SecretKeySpec(keyBytes, "AES");
            }
            // Dev fallback — not for production multi-instance deployments.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] derived = digest.digest("texto-local-webhook-dev-key".getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize webhook encryption key", exception);
        }
    }
}
