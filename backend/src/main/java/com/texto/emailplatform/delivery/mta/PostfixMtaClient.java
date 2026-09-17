package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;

/**
 * Production-oriented MTA client. Submits already-composed RFC 822 bytes to a configured Postfix
 * SMTP endpoint. Does not compose MIME, sign DKIM, or rewrite envelopes/headers.
 *
 * <p>Public Internet MX delivery stays disabled unless the application kill switch is on
 * <em>and</em> the Postfix image has confirmed public delivery.
 */
public class PostfixMtaClient implements MtaClient {

    private final EmailPlatformProperties properties;
    private final SmtpSubmitter submitter;
    private final PublicDeliveryGuard publicDeliveryGuard;

    public PostfixMtaClient(EmailPlatformProperties properties, SmtpSubmitter submitter) {
        this(properties, submitter, null);
    }

    public PostfixMtaClient(
            EmailPlatformProperties properties,
            SmtpSubmitter submitter,
            PublicDeliveryGuard publicDeliveryGuard
    ) {
        this.properties = properties;
        this.submitter = submitter;
        this.publicDeliveryGuard = publicDeliveryGuard;
    }

    @Override
    public MtaResult submit(MtaSubmitRequest request) {
        if (publicDeliveryGuard != null && !publicDeliveryGuard.allowMtaSubmit()) {
            return MtaResult.of(
                    MtaOutcome.PERMANENT_FAILURE,
                    "550",
                    "550 public Internet delivery is disabled",
                    "public_delivery_disabled",
                    "Public Internet delivery is disabled"
            );
        }
        return submitter.submit(SmtpEndpoint.postfix(properties), request);
    }
}
