package com.texto.emailplatform.delivery;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.springframework.stereotype.Component;

/**
 * Delivery destination for local development. Production SMTP is not implemented in this foundation.
 */
@Component
public class MailpitDeliveryDestination {

    private final EmailPlatformProperties properties;

    public MailpitDeliveryDestination(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    public String host() {
        return properties.getMailpit().getHost();
    }

    public int smtpPort() {
        return properties.getMailpit().getSmtpPort();
    }
}
