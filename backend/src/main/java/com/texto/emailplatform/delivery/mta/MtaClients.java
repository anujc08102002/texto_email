package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.util.Locale;

public final class MtaClients {

    public static final String MAILPIT = "mailpit";
    public static final String POSTFIX = "postfix";

    private MtaClients() {
    }

    public static String normalize(String implementation) {
        return implementation == null ? "" : implementation.trim().toLowerCase(Locale.ROOT);
    }

    public static MtaClient create(EmailPlatformProperties properties, SmtpSubmitter submitter) {
        String implementation = normalize(properties.getMta().getImplementation());
        return switch (implementation) {
            case MAILPIT -> new MailpitMtaClient(properties, submitter);
            case POSTFIX -> new PostfixMtaClient(properties, submitter);
            default -> throw new IllegalStateException(
                    "Unknown email-platform.mta.implementation '"
                            + properties.getMta().getImplementation()
                            + "'. Supported values: mailpit, postfix"
            );
        };
    }
}
