package com.texto.emailplatform.domain.dkim;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Production must configure {@code DKIM_KEY_ENCRYPTION_KEY}. Local/test may use the isolated fallback.
 */
@Component
public class DkimKeyEncryptionValidator implements InitializingBean {

    private final EmailPlatformProperties properties;
    private final Environment environment;

    public DkimKeyEncryptionValidator(EmailPlatformProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate(properties.getDomains().getDkimKeyEncryptionKey(), environment.acceptsProfiles("prod"));
    }

    static void validate(String configured, boolean production) {
        if (!production) {
            return;
        }
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Production requires DKIM_KEY_ENCRYPTION_KEY (Base64-encoded 32-byte AES key)");
        }
        DkimKeyProtector.resolveKey(configured);
    }
}
