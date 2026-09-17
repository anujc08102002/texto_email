package com.texto.emailplatform.email;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Tenant-scoped application send-rate limiter.
 *
 * <p>One accepted outbound <em>message</em> consumes one unit, regardless of recipient count.
 * Monthly quota remains recipient-based and is separate.
 *
 * <p>Algorithm: Redis Lua fixed window aligned to the UTC minute. {@code INCR} and
 * {@code EXPIRE} run in one script so concurrent requests cannot exceed the configured
 * limit. Rejected requests do not increment the counter.
 *
 * <p>Window boundary: a new UTC minute starts a new counter. A burst of
 * {@code 2 × limit} is possible across the last second of one minute and the first
 * second of the next.
 *
 * <p>Redis unavailable: fail-closed in every environment. Send acceptance is rejected
 * with {@code EMAIL_RATE_LIMIT_UNAVAILABLE}; Redis keys and diagnostics are never
 * returned to the client. Local compose includes Redis; do not treat an outage as
 * unlimited sending.
 */
@Component
public class EmailSendRateLimiter {

    public static final String ERROR_EXCEEDED = "EMAIL_RATE_LIMIT_EXCEEDED";
    public static final String ERROR_UNAVAILABLE = "EMAIL_RATE_LIMIT_UNAVAILABLE";

    private static final Logger log = LoggerFactory.getLogger(EmailSendRateLimiter.class);

    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>();

    static {
        ACQUIRE_SCRIPT.setResultType(List.class);
        ACQUIRE_SCRIPT.setScriptText("""
                local limit = tonumber(ARGV[1])
                local ttl = tonumber(ARGV[2])
                local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                if current >= limit then
                  local remaining = redis.call('TTL', KEYS[1])
                  if remaining < 1 then
                    remaining = ttl
                  end
                  return {0, remaining}
                end
                local n = redis.call('INCR', KEYS[1])
                if n == 1 or redis.call('TTL', KEYS[1]) < 0 then
                  redis.call('EXPIRE', KEYS[1], ttl)
                end
                local remaining = redis.call('TTL', KEYS[1])
                if remaining < 1 then
                  remaining = ttl
                end
                return {1, remaining}
                """);
        REFUND_SCRIPT.setResultType(Long.class);
        REFUND_SCRIPT.setScriptText("""
                local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                if current > 0 then
                  return redis.call('DECR', KEYS[1])
                end
                return 0
                """);
    }

    private final StringRedisTemplate redis;
    private final EmailPlatformProperties properties;
    private final EmailSendRateLimitMetrics metrics;
    private final Clock clock;

    @Autowired
    public EmailSendRateLimiter(
            StringRedisTemplate redis,
            EmailPlatformProperties properties,
            EmailSendRateLimitMetrics metrics
    ) {
        this(redis, properties, metrics, Clock.systemUTC());
    }

    EmailSendRateLimiter(
            StringRedisTemplate redis,
            EmailPlatformProperties properties,
            EmailSendRateLimitMetrics metrics,
            Clock clock
    ) {
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Consumes one send-rate unit for a new outbound acceptance.
     *
     * @return a lease that must be {@link #refund(Lease) refunded} if the message is not accepted
     */
    public Lease acquire(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        int limit = properties.getEmail().getRateLimitPerMinute();
        EmailSendRateLimitValidator.validate(limit);
        long epochSecond = clock.instant().getEpochSecond();
        long epochMinute = Math.floorDiv(epochSecond, 60);
        long ttlSeconds = 60 - Math.floorMod(epochSecond, 60);
        if (ttlSeconds < 1) {
            ttlSeconds = 1;
        }
        String key = "rate-limit:" + tenantId + ":email-send:" + epochMinute;
        try {
            @SuppressWarnings("unchecked")
            List<Object> result = redis.execute(
                    ACQUIRE_SCRIPT,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(ttlSeconds)
            );
            if (result == null || result.size() < 2) {
                metrics.recordError();
                throw unavailable();
            }
            long allowed = toLong(result.get(0));
            int retryAfter = Math.max(1, (int) toLong(result.get(1)));
            if (allowed != 1) {
                metrics.recordRejected();
                throw exceeded(retryAfter);
            }
            metrics.recordAllowed();
            return new Lease(key);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            metrics.recordError();
            log.warn("Email send rate limiter is unavailable");
            throw unavailable();
        }
    }

    /**
     * Returns a consumed unit when acceptance does not complete (quota failure, unique
     * idempotency collision, or any other rollback after acquire).
     */
    public void refund(Lease lease) {
        if (lease == null || lease.redisKey() == null || lease.redisKey().isBlank()) {
            return;
        }
        try {
            redis.execute(REFUND_SCRIPT, List.of(lease.redisKey()));
        } catch (RuntimeException exception) {
            log.warn("Email send rate limiter refund failed");
        }
    }

    private static ApiException exceeded(int retryAfterSeconds) {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ERROR_EXCEEDED,
                "Email send rate limit exceeded. Try again later.",
                retryAfterSeconds
        );
    }

    private static ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ERROR_UNAVAILABLE,
                "Email sending is temporarily unavailable"
        );
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        if (value instanceof byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
        throw new IllegalStateException("Unexpected Redis script result");
    }

    public record Lease(String redisKey) {
    }
}
