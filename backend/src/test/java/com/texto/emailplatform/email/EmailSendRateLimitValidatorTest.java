package com.texto.emailplatform.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailSendRateLimitValidatorTest {

    @Test
    void acceptsBounds() {
        assertThatCode(() -> EmailSendRateLimitValidator.validate(1)).doesNotThrowAnyException();
        assertThatCode(() -> EmailSendRateLimitValidator.validate(120)).doesNotThrowAnyException();
        assertThatCode(() -> EmailSendRateLimitValidator.validate(100_000)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroNegativeAndOverMax() {
        assertThatThrownBy(() -> EmailSendRateLimitValidator.validate(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate-limit-per-minute");
        assertThatThrownBy(() -> EmailSendRateLimitValidator.validate(-1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate-limit-per-minute");
        assertThatThrownBy(() -> EmailSendRateLimitValidator.validate(100_001))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate-limit-per-minute");
    }
}
