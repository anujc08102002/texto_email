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
        postfix.getMta().getSmtp().getStarttls().setEnabled(false);
        postfix.getMta().getSmtp().getStarttls().setRequired(false);
        assertThatThrownBy(() -> MtaPropertiesValidator.validate(postfix, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TLS");

        EmailPlatformProperties ready = properties("postfix");
        ready.getMta().getSmtp().getStarttls().setEnabled(true);
        ready.getMta().getSmtp().getStarttls().setRequired(true);
        MtaPropertiesValidator.validate(ready, true);
    }

    @Test
    void clientsAreSelectedByConfiguration() {
        SmtpSubmitter submitter = new SmtpSubmitter();
        assertThat(MtaClients.create(properties("mailpit"), submitter)).isInstanceOf(MailpitMtaClient.class);
        assertThat(MtaClients.create(properties("postfix"), submitter)).isInstanceOf(PostfixMtaClient.class);
        assertThatThrownBy(() -> MtaClients.create(properties("unknown"), submitter))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown");
    }

    private static EmailPlatformProperties properties(String implementation) {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation(implementation);
        properties.getMta().getSmtp().setHost("127.0.0.1");
        properties.getMta().getSmtp().setPort(2525);
        return properties;
    }
}
