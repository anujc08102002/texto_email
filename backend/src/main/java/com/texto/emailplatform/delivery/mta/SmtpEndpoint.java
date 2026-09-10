package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;

record SmtpEndpoint(
        String host,
        int port,
        int connectionTimeoutMs,
        int readTimeoutMs,
        int writeTimeoutMs,
        String ehloHostname,
        boolean startTlsEnabled,
        boolean startTlsRequired,
        boolean sslEnabled
) {

    static SmtpEndpoint from(EmailPlatformProperties properties) {
        var smtp = properties.getMta().getSmtp();
        String host = smtp.getHost();
        if (host == null || host.isBlank()) {
            host = properties.getMailpit().getHost();
        }
        int port = smtp.getPort() > 0 ? smtp.getPort() : properties.getMailpit().getSmtpPort();
        String ehlo = smtp.getEhloHostname() == null || smtp.getEhloHostname().isBlank()
                ? "texto.local"
                : smtp.getEhloHostname();
        return new SmtpEndpoint(
                host,
                port,
                smtp.getConnectionTimeoutMs(),
                smtp.getReadTimeoutMs(),
                smtp.getWriteTimeoutMs(),
                ehlo,
                smtp.getStarttls().isEnabled(),
                smtp.getStarttls().isRequired(),
                smtp.getSsl().isEnabled()
        );
    }

    static SmtpEndpoint postfix(EmailPlatformProperties properties) {
        var smtp = properties.getMta().getSmtp();
        String ehlo = smtp.getEhloHostname() == null || smtp.getEhloHostname().isBlank()
                ? "texto.local"
                : smtp.getEhloHostname();
        return new SmtpEndpoint(
                smtp.getHost(),
                smtp.getPort(),
                smtp.getConnectionTimeoutMs(),
                smtp.getReadTimeoutMs(),
                smtp.getWriteTimeoutMs(),
                ehlo,
                smtp.getStarttls().isEnabled(),
                smtp.getStarttls().isRequired(),
                smtp.getSsl().isEnabled()
        );
    }
}
