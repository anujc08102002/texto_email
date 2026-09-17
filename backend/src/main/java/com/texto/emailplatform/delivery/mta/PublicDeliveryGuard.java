package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Application-layer kill switch for public Internet MX submission.
 * Local/test profiles always allow MTA handoff (Mailpit / destination-locked Postfix).
 * Production external delivery submits only when {@code EMAIL_PUBLIC_DELIVERY_ENABLED=true}.
 */
@Component
public class PublicDeliveryGuard {

    private final EmailPlatformProperties properties;
    private final Environment environment;

    public PublicDeliveryGuard(EmailPlatformProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public boolean allowMtaSubmit() {
        return allowMtaSubmit(properties, environment.acceptsProfiles("prod"));
    }

    static boolean allowMtaSubmit(EmailPlatformProperties properties, boolean production) {
        if (!production) {
            return true;
        }
        String implementation = MtaClients.normalize(properties.getMta().getImplementation());
        if (!MtaClients.POSTFIX.equals(implementation) && !MtaClients.SES.equals(implementation)) {
            return true;
        }
        return properties.getMta().isPublicDeliveryEnabled();
    }

    public boolean production() {
        return environment.acceptsProfiles("prod");
    }
}
