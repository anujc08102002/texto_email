package com.texto.emailplatform.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class EmailSendRateLimiterRedisIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private EmailPlatformProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private Clock clock;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                redis.getHost(),
                redis.getMappedPort(6379)
        );
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        properties = new EmailPlatformProperties();
        properties.getEmail().setRateLimitPerMinute(3);
        meterRegistry = new SimpleMeterRegistry();
        clock = Clock.fixed(Instant.parse("2026-09-11T12:00:30Z"), ZoneOffset.UTC);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void underLimitExactlyAtLimitThenRejectsUntilNextWindow() {
        EmailSendRateLimiter limiter = limiter();
        UUID tenantId = UUID.randomUUID();

        limiter.acquire(tenantId);
        limiter.acquire(tenantId);
        limiter.acquire(tenantId);
        assertThatThrownBy(() -> limiter.acquire(tenantId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(429);
                    assertThat(exception.getCode()).isEqualTo(EmailSendRateLimiter.ERROR_EXCEEDED);
                    assertThat(exception.getRetryAfterSeconds()).isEqualTo(30);
                    assertThat(exception.getMessage()).doesNotContain("redis", tenantId.toString(), "rate-limit:");
                });

        EmailSendRateLimiter nextWindow = limiter(Clock.fixed(Instant.parse("2026-09-11T12:01:00Z"), ZoneOffset.UTC));
        nextWindow.acquire(tenantId);

        assertThat(meterRegistry.find(EmailSendRateLimitMetrics.METRIC_NAME).tag("result", "allowed").counter().count())
                .isEqualTo(4.0);
        assertThat(meterRegistry.find(EmailSendRateLimitMetrics.METRIC_NAME).tag("result", "rejected").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void tenantIsolation() {
        EmailSendRateLimiter limiter = limiter();
        properties.getEmail().setRateLimitPerMinute(1);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        limiter.acquire(tenantA);
        assertThatThrownBy(() -> limiter.acquire(tenantA)).isInstanceOf(ApiException.class);
        limiter.acquire(tenantB);
    }

    @Test
    void concurrentAcquiresDoNotExceedLimit() throws Exception {
        properties.getEmail().setRateLimitPerMinute(25);
        EmailSendRateLimiter limiter = limiter();
        UUID tenantId = UUID.randomUUID();
        int threads = 32;
        int attemptsPerThread = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int attempt = 0; attempt < attemptsPerThread; attempt++) {
                    try {
                        limiter.acquire(tenantId);
                        allowed.incrementAndGet();
                    } catch (ApiException exception) {
                        if (!EmailSendRateLimiter.ERROR_EXCEEDED.equals(exception.getCode())) {
                            throw exception;
                        }
                        rejected.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(allowed.get()).isEqualTo(25);
        assertThat(rejected.get()).isEqualTo(threads * attemptsPerThread - 25);
    }

    @Test
    void refundRestoresCapacity() {
        properties.getEmail().setRateLimitPerMinute(1);
        EmailSendRateLimiter limiter = limiter();
        UUID tenantId = UUID.randomUUID();
        EmailSendRateLimiter.Lease lease = limiter.acquire(tenantId);
        assertThatThrownBy(() -> limiter.acquire(tenantId)).isInstanceOf(ApiException.class);
        limiter.refund(lease);
        limiter.acquire(tenantId);
    }

    private EmailSendRateLimiter limiter() {
        return limiter(clock);
    }

    private EmailSendRateLimiter limiter(Clock limiterClock) {
        return new EmailSendRateLimiter(
                redisTemplate,
                properties,
                new EmailSendRateLimitMetrics(meterRegistry),
                limiterClock
        );
    }
}
