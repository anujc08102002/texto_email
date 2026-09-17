package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BounceAddressTest {

    @Test
    void formatsPlatformBounceAddress() {
        String token = "0123456789abcdef0123456789abcdef";
        assertThat(BounceAddress.format(token, "bounce.texto.test"))
                .isEqualTo("bounce+0123456789abcdef0123456789abcdef@bounce.texto.test");
    }

    @Test
    void mailFromFallsBackWhenTokenMissing() {
        assertThat(BounceAddress.mailFromOrFallback(null, "bounce.texto.test", "noreply@acme.texto.test"))
                .isEqualTo("noreply@acme.texto.test");
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> BounceAddress.format("short", "bounce.texto.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
