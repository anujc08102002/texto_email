package com.texto.emailplatform.delivery.mta;

/**
 * Already-composed RFC 822 message plus SMTP envelope. The MTA client must not
 * compose MIME, sign DKIM, or rewrite headers.
 */
public record MtaSubmitRequest(SmtpEnvelope envelope, byte[] rfc822) {

    public MtaSubmitRequest {
        rfc822 = rfc822 == null ? new byte[0] : rfc822.clone();
    }
}
