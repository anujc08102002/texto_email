package com.texto.emailplatform.domain.dkim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmDkimKeyProtectorTest {

    // A fixed, test-only Base64 32-byte AES key (not used anywhere else, safe to keep in tests).
    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private final AesGcmDkimKeyProtector protector = new AesGcmDkimKeyProtector(TEST_KEY, false);

    private static byte[] samplePkcs8() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return keyPair.getPrivate().getEncoded(); // PKCS#8
    }

    @Test
    void encryptThenDecryptReturnsOriginalBytes() throws Exception {
        byte[] pkcs8 = samplePkcs8();

        String protectedValue = protector.encryptPrivateKey(pkcs8);
        byte[] roundTrip = protector.decryptPrivateKey(protectedValue);

        assertThat(roundTrip).isEqualTo(pkcs8);
    }

    @Test
    void storedValueIsNotPlaintext() throws Exception {
        byte[] pkcs8 = samplePkcs8();

        String protectedValue = protector.encryptPrivateKey(pkcs8);

        assertThat(protectedValue).startsWith(AesGcmDkimKeyProtector.VERSION_PREFIX);
        // The ciphertext must not contain the raw or Base64-encoded private key.
        String plainBase64 = Base64.getEncoder().encodeToString(pkcs8);
        assertThat(protectedValue).doesNotContain(plainBase64);
        byte[] decodedCiphertext = Base64.getDecoder()
                .decode(protectedValue.substring(AesGcmDkimKeyProtector.VERSION_PREFIX.length()));
        assertThat(indexOf(decodedCiphertext, pkcs8)).isEqualTo(-1);
    }

    @Test
    void repeatedEncryptionUsesUniqueIvAndProducesDifferentCiphertext() throws Exception {
        byte[] pkcs8 = samplePkcs8();

        String first = protector.encryptPrivateKey(pkcs8);
        String second = protector.encryptPrivateKey(pkcs8);

        assertThat(first).isNotEqualTo(second);
        // Both still decrypt to the same plaintext.
        assertThat(protector.decryptPrivateKey(first)).isEqualTo(pkcs8);
        assertThat(protector.decryptPrivateKey(second)).isEqualTo(pkcs8);
    }

    @Test
    void tamperedCiphertextFailsAuthentication() throws Exception {
        String protectedValue = protector.encryptPrivateKey(samplePkcs8());
        byte[] raw = Base64.getDecoder()
                .decode(protectedValue.substring(AesGcmDkimKeyProtector.VERSION_PREFIX.length()));
        raw[raw.length - 1] ^= 0x01; // flip a bit in the tag/ciphertext
        String tampered = AesGcmDkimKeyProtector.VERSION_PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> protector.decryptPrivateKey(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyCannotDecrypt() throws Exception {
        String protectedValue = protector.encryptPrivateKey(samplePkcs8());
        AesGcmDkimKeyProtector otherProtector = new AesGcmDkimKeyProtector(OTHER_KEY, false);

        assertThatThrownBy(() -> otherProtector.decryptPrivateKey(protectedValue))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void malformedValueIsRejected() {
        assertThatThrownBy(() -> protector.decryptPrivateKey("not-a-dkim-ciphertext"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> protector.decryptPrivateKey(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingKeyInProductionFailsFast() {
        assertThatThrownBy(() -> new AesGcmDkimKeyProtector(null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required in production");
    }

    @Test
    void nonThirtyTwoByteKeyIsRejected() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> new AesGcmDkimKeyProtector(shortKey, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
