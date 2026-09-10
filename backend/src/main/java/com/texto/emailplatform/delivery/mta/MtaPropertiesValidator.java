package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MtaPropertiesValidator implements InitializingBean {

    private final EmailPlatformProperties properties;
    private final Environment environment;

    public MtaPropertiesValidator(EmailPlatformProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate(properties, environment.acceptsProfiles("prod"));
    }

    static void validate(EmailPlatformProperties properties, boolean production) {
        String implementation = MtaClients.normalize(properties.getMta().getImplementation());
        if (!MtaClients.MAILPIT.equals(implementation) && !MtaClients.POSTFIX.equals(implementation)) {
            throw new IllegalStateException(
                    "Unknown email-platform.mta.implementation '"
                            + properties.getMta().getImplementation()
                            + "'. Supported values: mailpit, postfix"
            );
        }

        var smtp = properties.getMta().getSmtp();
        boolean startTlsEnabled = smtp.getStarttls().isEnabled();
        boolean startTlsRequired = smtp.getStarttls().isRequired();
        boolean sslEnabled = smtp.getSsl().isEnabled();

        if (startTlsRequired && !startTlsEnabled) {
            throw new IllegalStateException(
                    "Invalid MTA TLS configuration: email-platform.mta.smtp.starttls.required=true requires starttls.enabled=true"
            );
        }
        if (sslEnabled && startTlsEnabled) {
            throw new IllegalStateException(
                    "Invalid MTA TLS configuration: implicit SSL and STARTTLS cannot both be enabled"
            );
        }

        if (MtaClients.POSTFIX.equals(implementation)) {
            if (smtp.getHost() == null || smtp.getHost().isBlank()) {
                throw new IllegalStateException("email-platform.mta.smtp.host is required when implementation=postfix");
            }
            if (smtp.getPort() <= 0) {
                throw new IllegalStateException("email-platform.mta.smtp.port is required when implementation=postfix");
            }
        }

        if (production) {
            if (!MtaClients.POSTFIX.equals(implementation)) {
                throw new IllegalStateException(
                        "Production requires email-platform.mta.implementation=postfix (Mailpit is local-only)"
                );
            }
            if (!sslEnabled && !(startTlsEnabled && startTlsRequired)) {
                throw new IllegalStateException(
                        "Production Postfix requires STARTTLS (enabled and required) or implicit SSL"
                );
            }
        }
    }
}
