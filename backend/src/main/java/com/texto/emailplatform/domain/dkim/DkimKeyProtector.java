package com.texto.emailplatform.domain.dkim;

/**
 * Application-level custody boundary for DKIM private-key material.
 *
 * <p>Deliberately a dedicated abstraction (not coupled to webhook-secret protection): DKIM signing
 * keys have different lifecycle and security requirements than webhook secrets. Keeping this
 * interface separate lets the application-level AES-GCM implementation be replaced with a KMS/HSM
 * (AWS KMS, GCP KMS, Azure Key Vault, ...) without touching {@code DomainVerificationService} or the
 * future DKIM signer.
 *
 * <p>Implementations MUST use authenticated encryption with a unique nonce per operation and MUST
 * never return or log plaintext key material.
 */
public interface DkimKeyProtector {

    /**
     * Encrypts a PKCS#8-encoded private key, returning an opaque, versioned ciphertext string safe
     * to persist. The same input MUST yield different ciphertext on each call (unique nonce).
     */
    String encryptPrivateKey(byte[] pkcs8PrivateKey);

    /**
     * Decrypts a value previously produced by {@link #encryptPrivateKey(byte[])} back into the
     * PKCS#8-encoded private key bytes. Throws if the ciphertext is tampered or the key is wrong.
     */
    byte[] decryptPrivateKey(String protectedValue);
}
