package com.texto.emailplatform.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainVerificationRateLimiterTest {

    @Test
    void rejectsRepeatedVerifyWithinWindow() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getDomains().setVerifyMaxPerMinute(2);
        DomainVerificationRateLimiter limiter = new DomainVerificationRateLimiter(properties);
        UUID tenantId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();

        limiter.check(tenantId, domainId);
        limiter.check(tenantId, domainId);
        assertThatThrownBy(() -> limiter.check(tenantId, domainId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "DNS_VERIFY_RATE_LIMITED");
    }
}
