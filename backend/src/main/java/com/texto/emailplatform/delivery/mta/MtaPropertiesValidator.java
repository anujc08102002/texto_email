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
        if (!MtaClients.MAILPIT.equals(implementation)
                && !MtaClients.POSTFIX.equals(implementation)
                && !MtaClients.SES.equals(implementation)) {
            throw new IllegalStateException(
                    "Unknown email-platform.mta.implementation '"
                            + properties.getMta().getImplementation()
                            + "'. Supported values: " + MtaClients.SUPPORTED_VALUES
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
        if (smtp.getConnectionTimeoutMs() <= 0 || smtp.getReadTimeoutMs() <= 0 || smtp.getWriteTimeoutMs() <= 0) {
            throw new IllegalStateException("email-platform.mta.smtp timeouts must be > 0");
        }
        if (properties.getEmail().getMaxRfc822Bytes() <= 0) {
            throw new IllegalStateException("email-platform.email.max-rfc822-bytes must be > 0");
        }

        if (MtaClients.POSTFIX.equals(implementation)) {
            if (smtp.getHost() == null || smtp.getHost().isBlank()) {
                throw new IllegalStateException("email-platform.mta.smtp.host is required when implementation=postfix");
            }
            if (smtp.getPort() <= 0) {
                throw new IllegalStateException("email-platform.mta.smtp.port is required when implementation=postfix");
            }
        }
        if (MtaClients.SES.equals(implementation)) {
            if (!properties.getSes().isEnabled()) {
                throw new IllegalStateException("email-platform.ses.enabled=true is required when implementation=ses");
            }
            if (properties.getSes().getRegion() == null || properties.getSes().getRegion().isBlank()) {
                throw new IllegalStateException("email-platform.ses.region is required when implementation=ses");
            }
        }

        if (production) {
            if (!MtaClients.POSTFIX.equals(implementation) && !MtaClients.SES.equals(implementation)) {
                throw new IllegalStateException(
                        "Production requires email-platform.mta.implementation=postfix or ses (Mailpit is local-only)"
                );
            }
            if (MtaClients.SES.equals(implementation)) {
                return;
            }
            if (smtp.getPort() == 1025) {
                throw new IllegalStateException(
                        "Production must not use SMTP port 1025 (Mailpit). Set MTA_SMTP_PORT to the Postfix submission port"
                );
            }
            String hostname = ProductionIdentity.normalize(properties.getMta().getHostname());
            if (!ProductionIdentity.isProductionHostname(hostname)) {
                throw new IllegalStateException(
                        "Production requires MTA_HOSTNAME to be a real FQDN (not localhost, *.local, or *.test)"
                );
            }
            String ehlo = ProductionIdentity.normalize(smtp.getEhloHostname());
            if (!ehlo.isEmpty() && !hostname.equals(ehlo)) {
                throw new IllegalStateException("Production MTA_SMTP_EHLO_HOSTNAME must match MTA_HOSTNAME");
            }
            String outboundIp = properties.getMta().getOutboundIp();
            if (!ProductionIdentity.isPublicIpv4(outboundIp)) {
                throw new IllegalStateException(
                        "Production requires MTA_OUTBOUND_IP to be a public IPv4 address (not loopback, RFC1918, or test ranges)"
                );
            }
            if (!sslEnabled && !(startTlsEnabled && startTlsRequired)) {
                throw new IllegalStateException(
                        "Production Postfix requires STARTTLS (enabled and required) or implicit SSL"
                );
            }
            String bounceDomain = ProductionIdentity.normalize(properties.getBounce().getDomain());
            if (!ProductionIdentity.isProductionBounceDomain(bounceDomain)) {
                throw new IllegalStateException(
                        "Production requires BOUNCE_DOMAIN to be a real DNS name (not localhost, *.local, or *.test)"
                );
            }
            String bounceMx = ProductionIdentity.normalize(properties.getBounce().getMxHostname());
            if (!bounceMx.isEmpty() && !ProductionIdentity.isProductionHostname(bounceMx)) {
                throw new IllegalStateException(
                        "Production BOUNCE_MX_HOSTNAME must be a real DNS name when set"
                );
            }
        }
    }

    public static String smtpIdentity(EmailPlatformProperties properties) {
        String hostname = ProductionIdentity.normalize(properties.getMta().getHostname());
        if (!hostname.isEmpty()) {
            return hostname;
        }
        return ProductionIdentity.normalize(properties.getMta().getSmtp().getEhloHostname());
    }
}
