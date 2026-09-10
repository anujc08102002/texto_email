package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.junit.jupiter.api.Test;

class MtaSettingsValidatorTest {

    @Test
    void rejectsUnknownImplementation() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation("postfix");
        MtaSettingsValidator validator = new MtaSettingsValidator(properties);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mailpit");
    }

    @Test
    void rejectsRequiredStartTlsWhenDisabled() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().getStarttls().setRequired(true);
        properties.getMta().getStarttls().setEnabled(false);
        MtaSettingsValidator validator = new MtaSettingsValidator(properties);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STARTTLS");
    }
}
