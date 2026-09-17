package com.texto.emailplatform.email;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * {@code email-platform.email.rate-limit-per-minute} must be a positive bound in every environment.
 * Production cannot disable send-rate limiting by setting 0 or a negative value.
 */
@Component
public class EmailSendRateLimitValidator implements InitializingBean {

    static final int MIN_PER_MINUTE = 1;
    static final int MAX_PER_MINUTE = 100_000;

    private final EmailPlatformProperties properties;

    public EmailSendRateLimitValidator(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        validate(properties.getEmail().getRateLimitPerMinute());
    }

    static void validate(int rateLimitPerMinute) {
        if (rateLimitPerMinute < MIN_PER_MINUTE || rateLimitPerMinute > MAX_PER_MINUTE) {
            throw new IllegalStateException(
                    "email-platform.email.rate-limit-per-minute must be between "
                            + MIN_PER_MINUTE
                            + " and "
                            + MAX_PER_MINUTE
            );
        }
    }
}
