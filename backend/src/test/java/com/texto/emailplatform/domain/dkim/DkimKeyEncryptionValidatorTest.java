package com.texto.emailplatform.domain.dkim;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class DkimKeyEncryptionValidatorTest {

    @Test
    void nonProductionAllowsMissingKey() {
        assertThatCode(() -> DkimKeyEncryptionValidator.validate(null, false)).doesNotThrowAnyException();
        assertThatCode(() -> DkimKeyEncryptionValidator.validate(" ", false)).doesNotThrowAnyException();
    }

    @Test
    void productionRequiresConfiguredKey() {
        assertThatThrownBy(() -> DkimKeyEncryptionValidator.validate(null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DKIM_KEY_ENCRYPTION_KEY");
        assertThatThrownBy(() -> DkimKeyEncryptionValidator.validate("   ", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DKIM_KEY_ENCRYPTION_KEY");
    }

    @Test
    void productionRejectsWrongLength() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> DkimKeyEncryptionValidator.validate(tooShort, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void productionAcceptsValidKey() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        assertThatCode(() -> DkimKeyEncryptionValidator.validate(key, true)).doesNotThrowAnyException();
    }
}
