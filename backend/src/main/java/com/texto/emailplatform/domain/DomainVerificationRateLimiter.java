package com.texto.emailplatform.domain;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * In-process debounce for manual DNS verification. Not a distributed limiter.
 */
@Component
public class DomainVerificationRateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final EmailPlatformProperties properties;

    public DomainVerificationRateLimiter(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    public void check(UUID tenantId, UUID domainId) {
        int max = Math.max(1, properties.getDomains().getVerifyMaxPerMinute());
        String key = tenantId + ":" + domainId;
        Instant now = Instant.now();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || Duration.between(existing.startedAt, now).toSeconds() >= 60) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt, existing.count + 1);
        });
        if (window.count > max) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "DNS_VERIFY_RATE_LIMITED",
                    "Please wait before verifying this domain again"
            );
        }
    }

    private record Window(Instant startedAt, int count) {
    }
}
