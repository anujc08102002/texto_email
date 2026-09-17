package com.texto.emailplatform.domain.dkim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DkimKeyProtectorTest {

    private static final byte[] KEY_A = Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    private static final byte[] KEY_B = Base64.getDecoder().decode("AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=");

    @Test
    void encryptDecryptRoundTrip() {
        DkimKeyProtector protector = DkimKeyProtector.withRawKey(KEY_A);
        byte[] plaintext = "hello-dkim-key".getBytes(StandardCharsets.UTF_8);
        String stored = protector.encrypt(plaintext);
        assertThat(stored).startsWith(DkimKeyProtector.VERSION_PREFIX);
        assertThat(protector.decrypt(stored)).isEqualTo(plaintext);
    }

    @Test
    void randomIvProducesDifferentCiphertext() {
        DkimKeyProtector protector = DkimKeyProtector.withRawKey(KEY_A);
        byte[] plaintext = "same-plaintext".getBytes(StandardCharsets.UTF_8);
        String first = protector.encrypt(plaintext);
        String second = protector.encrypt(plaintext);
        assertThat(first).isNotEqualTo(second);
        assertThat(protector.decrypt(first)).isEqualTo(plaintext);
        assertThat(protector.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void wrongEncryptionKeyFails() {
        String stored = DkimKeyProtector.withRawKey(KEY_A).encrypt("secret".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DkimKeyProtector.withRawKey(KEY_B).decrypt(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DKIM private key could not be decrypted");
    }

    @Test
    void corruptedCiphertextFails() {
        DkimKeyProtector protector = DkimKeyProtector.withRawKey(KEY_A);
        String stored = protector.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        String payload = stored.substring(DkimKeyProtector.VERSION_PREFIX.length());
        byte[] raw = Base64.getDecoder().decode(payload);
        raw[raw.length - 1] ^= 0x01;
        String corrupted = DkimKeyProtector.VERSION_PREFIX + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> protector.decrypt(corrupted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }

    @Test
    void malformedVersionFails() {
        DkimKeyProtector protector = DkimKeyProtector.withRawKey(KEY_A);
        assertThatThrownBy(() -> protector.decrypt("dk0:AAAA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported DKIM key ciphertext version");
        assertThatThrownBy(() -> protector.decrypt("dk1:%%%"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DKIM private key ciphertext is corrupted");
    }

    @Test
    void nullAndEmptyInputsFail() {
        DkimKeyProtector protector = DkimKeyProtector.withRawKey(KEY_A);
        assertThatThrownBy(() -> protector.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> protector.encrypt(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> protector.decrypt(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DKIM private key material is unavailable");
        assertThatThrownBy(() -> protector.decrypt(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DKIM private key material is unavailable");
    }

    @Test
    void withRawKeyRejectsWrongLength() {
        assertThatThrownBy(() -> DkimKeyProtector.withRawKey(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
