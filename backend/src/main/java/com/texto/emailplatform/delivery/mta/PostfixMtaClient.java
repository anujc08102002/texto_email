package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;

/**
 * Production-oriented MTA client. Submits already-composed RFC 822 bytes to a configured Postfix
 * SMTP endpoint. Does not compose MIME, sign DKIM, or rewrite envelopes/headers.
 *
 * <p>This phase only targets a controlled local Postfix. Public Internet delivery is not enabled.
 */
public class PostfixMtaClient implements MtaClient {

    private final EmailPlatformProperties properties;
    private final SmtpSubmitter submitter;

    public PostfixMtaClient(EmailPlatformProperties properties, SmtpSubmitter submitter) {
        this.properties = properties;
        this.submitter = submitter;
    }

    @Override
    public MtaResult submit(MtaSubmitRequest request) {
        return submitter.submit(SmtpEndpoint.postfix(properties), request);
    }
}
