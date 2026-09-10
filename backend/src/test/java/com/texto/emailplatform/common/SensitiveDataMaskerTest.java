package com.texto.emailplatform.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.texto.emailplatform.common.logging.SensitiveDataMasker;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    void masksPasswordsAndSecrets() {
        assertThat(SensitiveDataMasker.mask("password", "super-secret")).isEqualTo("supe****");
        assertThat(SensitiveDataMasker.mask("api-key", "abcd1234")).isEqualTo("abcd****");
        assertThat(SensitiveDataMasker.mask("Authorization", "Bearer token-value")).isEqualTo("Bear****");
    }

    @Test
    void leavesNonSensitiveValuesUnchanged() {
        assertThat(SensitiveDataMasker.mask("email", "ops@texto.dev")).isEqualTo("ops@texto.dev");
    }
}
