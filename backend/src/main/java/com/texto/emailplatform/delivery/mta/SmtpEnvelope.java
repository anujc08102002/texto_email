package com.texto.emailplatform.delivery.mta;

import java.util.List;

/**
 * SMTP envelope (MAIL FROM / RCPT TO / optional ENVID). Distinct from RFC 822 header addresses.
 */
public record SmtpEnvelope(String mailFrom, List<String> rcptTo, String envelopeId) {

    public SmtpEnvelope {
        rcptTo = rcptTo == null ? List.of() : List.copyOf(rcptTo);
        envelopeId = envelopeId == null || envelopeId.isBlank() ? null : envelopeId.trim();
    }

    public SmtpEnvelope(String mailFrom, List<String> rcptTo) {
        this(mailFrom, rcptTo, null);
    }
}
