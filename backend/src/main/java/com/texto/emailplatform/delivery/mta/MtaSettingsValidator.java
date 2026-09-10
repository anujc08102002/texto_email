package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Fails fast on unsupported MTA modes or contradictory TLS settings.
 */
@Component
public class MtaSettingsValidator {

    private final EmailPlatformProperties properties;

    public MtaSettingsValidator(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        EmailPlatformProperties.Mta mta = properties.getMta();
        String implementation = mta.getImplementation() == null ? "" : mta.getImplementation().trim().toLowerCase();
        if (!"mailpit".equals(implementation)) {
            throw new IllegalStateException(
                    "MTA implementation '" + mta.getImplementation()
                            + "' is not available. Use mailpit until Postfix is introduced."
            );
        }
        if (mta.getStarttls().isRequired() && !mta.getStarttls().isEnabled()) {
            throw new IllegalStateException("STARTTLS cannot be required when it is disabled");
        }
        if (mta.getSsl().isEnabled() && mta.getStarttls().isEnabled()) {
            throw new IllegalStateException("Implicit SSL and STARTTLS cannot both be enabled");
        }
        if (properties.resolvedMtaHost() == null || properties.resolvedMtaHost().isBlank()) {
            throw new IllegalStateException("MTA SMTP host is required");
        }
        if (properties.resolvedMtaPort() <= 0) {
            throw new IllegalStateException("MTA SMTP port is required");
        }
    }
}
