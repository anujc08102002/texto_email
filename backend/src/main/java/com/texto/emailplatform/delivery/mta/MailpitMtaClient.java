package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;

/**
 * Local mailbox/testing MTA. Submits already-composed RFC 822 bytes to Mailpit SMTP.
 */
public class MailpitMtaClient implements MtaClient {

    private final EmailPlatformProperties properties;
    private final SmtpSubmitter submitter;

    public MailpitMtaClient(EmailPlatformProperties properties, SmtpSubmitter submitter) {
        this.properties = properties;
        this.submitter = submitter;
    }

    @Override
    public MtaResult submit(MtaSubmitRequest request) {
        return submitter.submit(SmtpEndpoint.from(properties), request);
    }
}
