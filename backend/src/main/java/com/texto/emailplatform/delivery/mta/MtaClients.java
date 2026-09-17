package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.util.Locale;

public final class MtaClients {

    public static final String MAILPIT = "mailpit";
    public static final String POSTFIX = "postfix";
    public static final String SES = "ses";
    public static final String SUPPORTED_VALUES = "mailpit, postfix, ses";

    private MtaClients() {
    }

    public static String normalize(String implementation) {
        return implementation == null ? "" : implementation.trim().toLowerCase(Locale.ROOT);
    }

    public static MtaClient create(EmailPlatformProperties properties, SmtpSubmitter submitter) {
        return create(properties, submitter, null);
    }

    public static MtaClient create(
            EmailPlatformProperties properties,
            SmtpSubmitter submitter,
            PublicDeliveryGuard publicDeliveryGuard
    ) {
        return create(properties, submitter, publicDeliveryGuard, null);
    }

    public static MtaClient create(
            EmailPlatformProperties properties,
            SmtpSubmitter submitter,
            PublicDeliveryGuard publicDeliveryGuard,
            SesMtaClient sesMtaClient
    ) {
        String implementation = normalize(properties.getMta().getImplementation());
        return switch (implementation) {
            case MAILPIT -> new MailpitMtaClient(properties, submitter);
            case POSTFIX -> new PostfixMtaClient(properties, submitter, publicDeliveryGuard);
            case SES -> {
                if (sesMtaClient == null) {
                    throw new IllegalStateException("SES MTA client is required when implementation=ses");
                }
                yield sesMtaClient;
            }
            default -> throw new IllegalStateException(
                    "Unknown email-platform.mta.implementation '"
                            + properties.getMta().getImplementation()
                            + "'. Supported values: " + SUPPORTED_VALUES
            );
        };
    }
}
