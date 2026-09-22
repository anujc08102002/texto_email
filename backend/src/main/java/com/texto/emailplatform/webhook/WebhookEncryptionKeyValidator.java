package com.texto.emailplatform.webhook;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.util.Base64;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Prevents production from encrypting webhook secrets with the local fallback key. */
@Component
public class WebhookEncryptionKeyValidator implements InitializingBean {
    private final EmailPlatformProperties properties;
    private final Environment environment;

    public WebhookEncryptionKeyValidator(EmailPlatformProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate(properties.getWebhooks().getEncryptionKey(), environment.acceptsProfiles("prod"));
    }

    static void validate(String configured, boolean production) {
        if (!production) return;
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Production requires WEBHOOKS_ENCRYPTION_KEY");
        }
        try {
            if (Base64.getDecoder().decode(configured.trim()).length != 32) {
                throw new IllegalStateException("WEBHOOKS_ENCRYPTION_KEY must decode to 32 bytes");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("WEBHOOKS_ENCRYPTION_KEY must be valid Base64", exception);
        }
    }
}
