package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.junit.jupiter.api.Test;

class MtaPropertiesValidatorTest {

    @Test
    void unknownImplementationFailsFast() {
        EmailPlatformProperties properties = properties("sendgrid");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sendgrid");
    }

    @Test
    void mailpitIsValidLocally() {
        MtaPropertiesValidator.validate(properties("mailpit"), false);
    }

    @Test
    void sesRequiresEnabledAndRegion() {
        EmailPlatformProperties disabled = properties("ses");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(disabled, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ses.enabled");

        EmailPlatformProperties noRegion = sesProperties();
        noRegion.getSes().setRegion(" ");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(noRegion, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ses.region");

        MtaPropertiesValidator.validate(sesProperties(), false);
        MtaPropertiesValidator.validate(sesProperties(), true);
    }

    @Test
    void postfixRequiresHost() {
        EmailPlatformProperties properties = properties("postfix");
        properties.getMta().getSmtp().setHost(" ");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("smtp.host");
    }

    @Test
    void requiredStartTlsWithoutEnabledFails() {
        EmailPlatformProperties properties = properties("mailpit");
        properties.getMta().getSmtp().getStarttls().setEnabled(false);
        properties.getMta().getSmtp().getStarttls().setRequired(true);
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("starttls");
    }

    @Test
    void sslAndStartTlsTogetherFail() {
        EmailPlatformProperties properties = properties("postfix");
        properties.getMta().getSmtp().getStarttls().setEnabled(true);
        properties.getMta().getSmtp().getSsl().setEnabled(true);
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot both be enabled");
    }

    @Test
    void productionRequiresPostfixAndTls() {
        EmailPlatformProperties mailpit = properties("mailpit");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(mailpit, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postfix");

        EmailPlatformProperties postfix = properties("postfix");
        postfix.getMta().setHostname("smtp.texto.email");
        postfix.getMta().getSmtp().setEhloHostname("smtp.texto.email");
        postfix.getMta().setOutboundIp("1.1.1.1");
        postfix.getMta().getSmtp().setPort(25);
        postfix.getBounce().setDomain("bounce.texto.email");
        postfix.getMta().getSmtp().getStarttls().setEnabled(false);
        postfix.getMta().getSmtp().getStarttls().setRequired(false);
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(postfix, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TLS");

        EmailPlatformProperties ready = properties("postfix");
        ready.getMta().getSmtp().getStarttls().setEnabled(true);
        ready.getMta().getSmtp().getStarttls().setRequired(true);
        ready.getMta().setHostname("smtp.texto.email");
        ready.getMta().getSmtp().setEhloHostname("smtp.texto.email");
        ready.getMta().setOutboundIp("1.1.1.1");
        ready.getMta().getSmtp().setPort(25);
        ready.getBounce().setDomain("bounce.texto.email");
        MtaPropertiesValidator.validate(ready, true);
    }

    @Test
    void productionRequiresBounceDomain() {
        EmailPlatformProperties properties = productionReady();
        properties.getBounce().setDomain(" ");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOUNCE_DOMAIN");

        properties.getBounce().setDomain("bounce.texto.test");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOUNCE_DOMAIN");

        properties.getBounce().setDomain("localhost");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOUNCE_DOMAIN");
    }

    @Test
    void productionRequiresRealHostname() {
        EmailPlatformProperties properties = productionReady();
        properties.getMta().setHostname("");
        properties.getMta().getSmtp().setEhloHostname(" ");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FQDN");
    }

    @Test
    void productionRejectsPrivateOutboundIpAndTestHostname() {
        EmailPlatformProperties privateIp = productionReady();
        privateIp.getMta().setOutboundIp("10.0.0.1");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(privateIp, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MTA_OUTBOUND_IP");

        EmailPlatformProperties testHost = productionReady();
        testHost.getMta().setHostname("smtp.texto.test");
        testHost.getMta().getSmtp().setEhloHostname("smtp.texto.test");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(testHost, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MTA_HOSTNAME");
    }

    @Test
    void publicDeliveryDefaultsOff() {
        EmailPlatformProperties properties = properties("postfix");
        assertThat(properties.getMta().isPublicDeliveryEnabled()).isFalse();
        MtaPropertiesValidator.validate(productionReady(), true);
        assertThat(productionReady().getMta().isPublicDeliveryEnabled()).isFalse();
    }

    @Test
    void productionRejectsMailpitPortAndLocalEhlo() {
        EmailPlatformProperties mailpitPort = properties("postfix");
        mailpitPort.getMta().getSmtp().getStarttls().setEnabled(true);
        mailpitPort.getMta().getSmtp().getStarttls().setRequired(true);
        mailpitPort.getMta().setHostname("smtp.texto.email");
        mailpitPort.getMta().getSmtp().setEhloHostname("smtp.texto.email");
        mailpitPort.getMta().setOutboundIp("1.1.1.1");
        mailpitPort.getMta().getSmtp().setPort(1025);
        mailpitPort.getBounce().setDomain("bounce.texto.email");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(mailpitPort, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1025");

        EmailPlatformProperties localEhlo = properties("postfix");
        localEhlo.getMta().getSmtp().getStarttls().setEnabled(true);
        localEhlo.getMta().getSmtp().getStarttls().setRequired(true);
        localEhlo.getMta().setHostname("texto.local");
        localEhlo.getMta().getSmtp().setEhloHostname("texto.local");
        localEhlo.getMta().setOutboundIp("1.1.1.1");
        localEhlo.getMta().getSmtp().setPort(25);
        localEhlo.getBounce().setDomain("bounce.texto.email");
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(localEhlo, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MTA_HOSTNAME");
    }

    @Test
    void timeoutsMustBePositive() {
        EmailPlatformProperties properties = properties("mailpit");
        properties.getMta().getSmtp().setConnectionTimeoutMs(0);
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeouts");
    }

    @Test
    void incompleteTlsFailsFast() {
        EmailPlatformProperties properties = properties("postfix");
        properties.getMta().getSmtp().getStarttls().setRequired(true);
        properties.getMta().getSmtp().getStarttls().setEnabled(false);
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(properties, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("starttls");
    }

    @Test
    void clientsAreSelectedByConfiguration() {
        SmtpSubmitter submitter = new SmtpSubmitter();
        SesMtaClient sesMtaClient = org.mockito.Mockito.mock(SesMtaClient.class);
        assertThat(MtaClients.create(properties("mailpit"), submitter)).isInstanceOf(MailpitMtaClient.class);
        assertThat(MtaClients.create(properties("postfix"), submitter)).isInstanceOf(PostfixMtaClient.class);
        assertThat(MtaClients.create(properties("ses"), submitter, null, sesMtaClient)).isSameAs(sesMtaClient);
        assertThatThrownBy(() -> MtaClients.create(properties("unknown"), submitter))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown")
                .hasMessageContaining("mailpit, postfix, ses");
    }

    private static EmailPlatformProperties productionReady() {
        EmailPlatformProperties properties = properties("postfix");
        properties.getMta().getSmtp().getStarttls().setEnabled(true);
        properties.getMta().getSmtp().getStarttls().setRequired(true);
        properties.getMta().setHostname("smtp.texto.email");
        properties.getMta().getSmtp().setEhloHostname("smtp.texto.email");
        properties.getMta().setOutboundIp("1.1.1.1");
        properties.getMta().getSmtp().setPort(25);
        properties.getBounce().setDomain("bounce.texto.email");
        return properties;
    }

    private static EmailPlatformProperties properties(String implementation) {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation(implementation);
        properties.getMta().getSmtp().setHost("127.0.0.1");
        properties.getMta().getSmtp().setPort(2525);
        return properties;
    }

    private static EmailPlatformProperties sesProperties() {
        EmailPlatformProperties properties = properties("ses");
        properties.getSes().setEnabled(true);
        properties.getSes().setRegion("ap-south-1");
        return properties;
    }
}
