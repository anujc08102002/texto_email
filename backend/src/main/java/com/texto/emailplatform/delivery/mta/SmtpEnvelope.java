package com.texto.emailplatform.delivery.mta;

import java.util.List;

/**
 * SMTP envelope (MAIL FROM / RCPT TO). Distinct from MIME From/To headers.
 */
public record SmtpEnvelope(String mailFrom, List<String> recipients) {

    public SmtpEnvelope {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
    }
}
