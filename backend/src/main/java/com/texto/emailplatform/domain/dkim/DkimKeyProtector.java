package com.texto.emailplatform.domain.dkim;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM protector for DKIM private keys at rest.
 * Ciphertext format: {@code dk1:<base64(iv || ciphertext+tag)>}.
 * Replaceable later by KMS/HSM. Never logs key material.
 */
@Component
public class DkimKeyProtector {

    public static final String VERSION_PREFIX = "dk1:";
    static final int GCM_IV_LENGTH = 12;
    static final int GCM_TAG_BITS = 128;
    static final int KEY_LENGTH = 32;

    private final SecretKey secretKey;

    @Autowired
    public DkimKeyProtector(EmailPlatformProperties properties) {
        this(resolveKey(properties.getDomains().getDkimKeyEncryptionKey()));
    }

    private DkimKeyProtector(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    public static DkimKeyProtector withRawKey(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("DKIM encryption key must be 32 bytes");
        }
        return new DkimKeyProtector(new SecretKeySpec(keyBytes.clone(), "AES"));
    }

    public String encrypt(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("DKIM private key material is required");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect DKIM private key", exception);
        }
    }

    public byte[] decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            throw new IllegalStateException("DKIM private key material is unavailable");
        }
        if (!stored.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("Unsupported DKIM key ciphertext version");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()).trim());
            if (raw.length <= GCM_IV_LENGTH + 16) {
                throw new IllegalStateException("DKIM private key ciphertext is corrupted");
            }
            byte[] iv = Arrays.copyOfRange(raw, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(raw, GCM_IV_LENGTH, raw.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException exception) {
            throw new IllegalStateException("DKIM private key could not be decrypted");
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException("DKIM private key ciphertext is corrupted");
        }
    }

    static SecretKey resolveKey(String configured) {
        try {
            if (configured != null && !configured.isBlank()) {
                byte[] keyBytes = Base64.getDecoder().decode(configured.trim());
                if (keyBytes.length != KEY_LENGTH) {
                    throw new IllegalStateException("DKIM_KEY_ENCRYPTION_KEY must be 32 bytes (Base64-encoded)");
                }
                return new SecretKeySpec(keyBytes, "AES");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] derived = digest.digest("texto-local-dkim-dev-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DKIM_KEY_ENCRYPTION_KEY must be 32 bytes (Base64-encoded)");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize DKIM encryption key");
        }
    }
}
