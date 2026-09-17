package com.texto.emailplatform.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class EmailSendRateLimiterFailureTest {

    @Mock
    private StringRedisTemplate redis;

    private EmailSendRateLimiter limiter;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getEmail().setRateLimitPerMinute(120);
        meterRegistry = new SimpleMeterRegistry();
        limiter = new EmailSendRateLimiter(redis, properties, new EmailSendRateLimitMetrics(meterRegistry));
    }

    @Test
    void redisOutageFailsClosedWithoutLeakingInternals() {
        UUID tenantId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(redis.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> limiter.acquire(tenantId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(503);
                    assertThat(exception.getCode()).isEqualTo(EmailSendRateLimiter.ERROR_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("Email sending is temporarily unavailable");
                    assertThat(exception.getMessage().toLowerCase()).doesNotContain("redis", "lettuce", "rate-limit:");
                    assertThat(exception.getMessage()).doesNotContain(tenantId.toString());
                });

        assertThat(meterRegistry.find(EmailSendRateLimitMetrics.METRIC_NAME)
                .tag("result", "error")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
