package com.texto.emailplatform.delivery.mta;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.angus.mail.smtp.SMTPMessage;

/**
 * Jakarta Mail message that emits the original RFC 822 bytes on {@code DATA}.
 * Envelope MAIL FROM is set separately and does not rewrite MIME headers.
 */
final class ImmutableSmtpMessage extends SMTPMessage {

    private final byte[] rfc822;

    ImmutableSmtpMessage(Session session, byte[] rfc822, String envelopeFrom) throws MessagingException {
        super(session, new ByteArrayInputStream(rfc822));
        this.rfc822 = rfc822;
        setEnvelopeFrom(envelopeFrom);
        this.saved = true;
        this.modified = false;
    }

    @Override
    public void writeTo(OutputStream os, String[] ignoreList) throws IOException, MessagingException {
        os.write(rfc822);
        os.flush();
    }
}
