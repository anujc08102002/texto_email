package com.texto.emailplatform.domain.dkim;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Application-level {@link DkimKeyProtector} using AES-256-GCM (authenticated encryption).
 *
 * <p>Ciphertext format: {@code "dk1:" + base64(iv || ciphertext||tag)} with a fresh random 12-byte
 * IV per operation and a 128-bit tag. The version prefix ({@code dk1}) allows the scheme (or a KMS
 * backend) to evolve without ambiguity.
 *
 * <p>The master key comes from {@code email-platform.dkim.encryption-key} (Base64 32-byte AES key),
 * i.e. the {@code DKIM_KEY_ENCRYPTION_KEY} environment variable. In the {@code prod} profile the key
 * is REQUIRED and the bean fails fast at startup if it is missing. In non-prod (local/test) a
 * process-local derived key is used so development works without configuration.
 */
@Component
public class AesGcmDkimKeyProtector implements DkimKeyProtector {

    static final String VERSION_PREFIX = "dk1:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_256_KEY_BYTES = 32;

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AesGcmDkimKeyProtector(EmailPlatformProperties properties, Environment environment) {
        boolean requireConfigured = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        this.masterKey = resolveKey(properties.getDkim().getEncryptionKey(), requireConfigured);
    }

    // Visible for tests: construct with an explicit key requirement.
    AesGcmDkimKeyProtector(String configuredKey, boolean requireConfigured) {
        this.masterKey = resolveKey(configuredKey, requireConfigured);
    }

    @Override
    public String encryptPrivateKey(byte[] pkcs8PrivateKey) {
        if (pkcs8PrivateKey == null || pkcs8PrivateKey.length == 0) {
            throw new IllegalArgumentException("private key material must not be empty");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(pkcs8PrivateKey);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            // Never include key material in diagnostics.
            throw new IllegalStateException("Unable to encrypt DKIM private key", exception);
        }
    }

    @Override
    public byte[] decryptPrivateKey(String protectedValue) {
        if (protectedValue == null || !protectedValue.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("DKIM private key material is unavailable or malformed");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(protectedValue.substring(VERSION_PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(raw, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(raw, GCM_IV_LENGTH, raw.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            // Authentication failure (tamper / wrong key) or malformed ciphertext.
            throw new IllegalStateException("Unable to decrypt DKIM private key", exception);
        }
    }

    private static SecretKey resolveKey(String configured, boolean requireConfigured) {
        if (configured != null && !configured.isBlank()) {
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(configured.trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("DKIM encryption key must be a Base64-encoded value");
            }
            if (keyBytes.length != AES_256_KEY_BYTES) {
                throw new IllegalStateException(
                        "DKIM encryption key must decode to 32 bytes (AES-256); generate one with `openssl rand -base64 32`"
                );
            }
            return new SecretKeySpec(keyBytes, "AES");
        }
        if (requireConfigured) {
            throw new IllegalStateException(
                    "email-platform.dkim.encryption-key (DKIM_KEY_ENCRYPTION_KEY) is required in production but is not configured"
            );
        }
        // Development/test fallback only — deterministic, process-local, never for production.
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] derived = digest.digest("texto-local-dkim-dev-key".getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize DKIM encryption key", exception);
        }
    }
}
