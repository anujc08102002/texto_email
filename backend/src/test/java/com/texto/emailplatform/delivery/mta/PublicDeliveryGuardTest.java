package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.junit.jupiter.api.Test;

class PublicDeliveryGuardTest {

    @Test
    void nonProductionAlwaysAllowsHandoff() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation("postfix");
        properties.getMta().setPublicDeliveryEnabled(false);
        assertThat(PublicDeliveryGuard.allowMtaSubmit(properties, false)).isTrue();
    }

    @Test
    void productionPostfixIsBlockedWhenKillSwitchOff() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation("postfix");
        properties.getMta().setPublicDeliveryEnabled(false);
        assertThat(PublicDeliveryGuard.allowMtaSubmit(properties, true)).isFalse();
    }

    @Test
    void productionSesIsBlockedWhenKillSwitchOff() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation("ses");
        properties.getMta().setPublicDeliveryEnabled(false);
        assertThat(PublicDeliveryGuard.allowMtaSubmit(properties, true)).isFalse();
    }

    @Test
    void productionPostfixIsAllowedOnlyWhenKillSwitchOn() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation("postfix");
        properties.getMta().setPublicDeliveryEnabled(true);
        assertThat(PublicDeliveryGuard.allowMtaSubmit(properties, true)).isTrue();
    }

    @Test
    void productionSesIsAllowedOnlyWhenKillSwitchOn() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation("ses");
        properties.getMta().setPublicDeliveryEnabled(true);
        assertThat(PublicDeliveryGuard.allowMtaSubmit(properties, true)).isTrue();
    }
}
