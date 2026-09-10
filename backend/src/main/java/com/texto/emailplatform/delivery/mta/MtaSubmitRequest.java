package com.texto.emailplatform.delivery.mta;

/**
 * Fully composed RFC 822 message plus SMTP envelope.
 *
 * <p>{@code rfc822} is immutable for transport purposes: the MTA client must submit
 * these exact bytes and must not regenerate DKIM or rewrite signed headers.
 */
public record MtaSubmitRequest(SmtpEnvelope envelope, byte[] rfc822, String messageId) {

    public MtaSubmitRequest {
        rfc822 = rfc822 == null ? new byte[0] : rfc822.clone();
    }

    public byte[] rfc822() {
        return rfc822.clone();
    }
}
