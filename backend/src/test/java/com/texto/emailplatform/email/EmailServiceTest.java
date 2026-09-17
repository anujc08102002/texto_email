package com.texto.emailplatform.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.domain.DomainService;
import com.texto.emailplatform.email.api.SendEmailRequest;
import com.texto.emailplatform.email.api.SendTestEmailRequest;
import com.texto.emailplatform.email.domain.DeliveryAttemptRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.outbox.OutboxEventEntity;
import com.texto.emailplatform.outbox.OutboxService;
import com.texto.emailplatform.plan.FeatureCodes;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.template.TemplateService;
import com.texto.emailplatform.tenant.domain.TenantEntity;
import com.texto.emailplatform.tenant.domain.TenantRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import com.texto.emailplatform.usage.UsageMetrics;
import com.texto.emailplatform.usage.UsageService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import com.texto.emailplatform.webhook.WebhookEventTypes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private DomainService domainService;

    @Mock
    private SuppressionService suppressionService;

    @Mock
    private TemplateService templateService;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private UsageService usageService;

    @Mock
    private WebhookEventPublisher webhookEventPublisher;

    @Mock
    private OutboxService outboxService;

    @Mock
    private EmailSendRateLimiter emailSendRateLimiter;

    private EmailService emailService;

    private final UUID tenantId =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void bindTenant() {
        TenantContext.set(tenantId);
        SecurityContextHolder.clearContext();

        EmailPlatformProperties properties = new EmailPlatformProperties();

        emailService = new EmailService(
                emailMessageRepository,
                deliveryAttemptRepository,
                tenantRepository,
                domainService,
                suppressionService,
                templateService,
                entitlementService,
                usageService,
                webhookEventPublisher,
                outboxService,
                emailSendRateLimiter,
                properties
        );

        lenient()
                .when(emailMessageRepository.save(any(EmailMessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(entitlementService.hasFeature(
                tenantId,
                FeatureCodes.API_SENDING
        )).thenReturn(true);

        when(entitlementService.canUseFeature(
                tenantId,
                FeatureCodes.API_SENDING
        )).thenReturn(true);

        lenient()
                .when(suppressionService.isSuppressed(eq(tenantId), anyString()))
                .thenReturn(false);

        lenient()
                .when(emailSendRateLimiter.acquire(tenantId))
                .thenReturn(
                        new EmailSendRateLimiter.Lease("rate-limit-test")
                );
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendTestQueuesMessageAndEnqueuesOutbox() {
        var response = emailService.sendTest(
                new SendTestEmailRequest(
                        "noreply@example.com",
                        "qa@example.com",
                        "Hello",
                        "Body"
                )
        );

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.recipient()).isEqualTo("qa@example.com");

        verify(usageService).consumeQuota(
                tenantId,
                UsageMetrics.MONTHLY_EMAILS,
                1
        );

        verify(domainService).requireVerifiedSender(
                eq(tenantId),
                eq("noreply@example.com")
        );

        verify(outboxService).enqueue(
                eq(tenantId),
                eq(OutboxEventEntity.AGGREGATE_EMAIL_MESSAGE),
                any(UUID.class),
                eq(OutboxEventEntity.EMAIL_DELIVERY_REQUESTED),
                any(Map.class)
        );

        verify(webhookEventPublisher).publishEmailEvent(
                eq(WebhookEventTypes.EMAIL_QUEUED),
                any(EmailMessageEntity.class)
        );

        verify(emailSendRateLimiter).acquire(tenantId);
    }

    @Test
    void sendTestMarksSuppressedWithoutQuotaOrOutbox() {
        when(suppressionService.isSuppressed(
                tenantId,
                "blocked@example.com"
        )).thenReturn(true);

        var response = emailService.sendTest(
                new SendTestEmailRequest(
                        "noreply@example.com",
                        "blocked@example.com",
                        "Hello",
                        "Body"
                )
        );

        assertThat(response.status()).isEqualTo("SUPPRESSED");

        verify(usageService, never()).consumeQuota(
                eq(tenantId),
                anyString(),
                anyLong()
        );

        verify(outboxService, never()).enqueue(
                any(),
                any(),
                any(),
                any(),
                any()
        );

        verify(webhookEventPublisher).publishEmailEvent(
                eq(WebhookEventTypes.EMAIL_SUPPRESSED),
                any(EmailMessageEntity.class)
        );

        verify(emailSendRateLimiter, never()).acquire(any());
    }

    @Test
    void sendTestPersistsQueuedEntity() {
        emailService.sendTest(
                new SendTestEmailRequest(
                        "noreply@example.com",
                        "qa@example.com",
                        "Hello",
                        "Body"
                )
        );

        ArgumentCaptor<EmailMessageEntity> captor =
                ArgumentCaptor.forClass(EmailMessageEntity.class);

        verify(emailMessageRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus())
                .isEqualTo("QUEUED");

        assertThat(captor.getValue().getQueuedAt())
                .isNotNull();
    }

    @Test
    void rateLimitRejectsWithoutQuotaOrOutbox() {
        when(emailSendRateLimiter.acquire(tenantId))
                .thenThrow(new ApiException(
                        429,
                        EmailSendRateLimiter.ERROR_EXCEEDED,
                        "Email send rate limit exceeded. Try again later.",
                        40
                ));

        assertThatThrownBy(() -> emailService.sendTest(
                new SendTestEmailRequest(
                        "noreply@example.com",
                        "qa@example.com",
                        "Hello",
                        "Body"
                )
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus())
                                    .isEqualTo(429);

                            assertThat(exception.getCode())
                                    .isEqualTo(
                                            EmailSendRateLimiter.ERROR_EXCEEDED
                                    );

                            assertThat(exception.getMessage())
                                    .doesNotContain(
                                            "redis",
                                            "rate-limit:",
                                            tenantId.toString()
                                    );
                        }
                );

        verify(usageService, never()).consumeQuota(
                eq(tenantId),
                anyString(),
                anyLong()
        );

        verify(outboxService, never()).enqueue(
                any(),
                any(),
                any(),
                any(),
                any()
        );

        verify(emailMessageRepository, never())
                .save(any());

        verify(emailSendRateLimiter, never())
                .refund(any());
    }

    @Test
    void idempotentReplayDoesNotConsumeRateLimit() {
        EmailMessageEntity existing = EmailMessageEntity.create(
                tenantId,
                "noreply@acme.texto.test",
                List.of("one@example.com"),
                List.of(),
                List.of(),
                List.of(),
                null,
                "Idem",
                null,
                "body",
                null,
                null,
                "same-key",
                Map.of(),
                5,
                null
        );

        when(emailMessageRepository.findByTenantIdAndIdempotencyKey(
                tenantId,
                "same-key"
        )).thenReturn(Optional.of(existing));

        var response = emailService.send(
                sendRequest(List.of("one@example.com")),
                "same-key"
        );

        assertThat(response.id()).isEqualTo(existing.getId());

        verify(emailSendRateLimiter, never())
                .acquire(any());

        verify(usageService, never()).consumeQuota(
                eq(tenantId),
                anyString(),
                anyLong()
        );

        verify(outboxService, never()).enqueue(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void multiRecipientMessageConsumesOneRateLimitUnit() {
        var response = emailService.send(
                sendRequest(
                        List.of(
                                "a@example.com",
                                "b@example.com",
                                "c@example.com"
                        )
                ),
                null
        );

        assertThat(response.status())
                .isEqualTo("QUEUED");

        verify(emailSendRateLimiter)
                .acquire(tenantId);

        verify(usageService).consumeQuota(
                tenantId,
                UsageMetrics.MONTHLY_EMAILS,
                3
        );

        verify(outboxService).enqueue(
                eq(tenantId),
                eq(OutboxEventEntity.AGGREGATE_EMAIL_MESSAGE),
                any(UUID.class),
                eq(OutboxEventEntity.EMAIL_DELIVERY_REQUESTED),
                any(Map.class)
        );
    }

    @Test
    void quotaFailureRefundsRateLimitAndDoesNotEnqueue() {
        doThrow(new ApiException(
                429,
                "QUOTA_EXCEEDED",
                "Plan quota exhausted"
        ))
                .when(usageService)
                .consumeQuota(
                        eq(tenantId),
                        eq(UsageMetrics.MONTHLY_EMAILS),
                        eq(1L)
                );

        assertThatThrownBy(() ->
                emailService.send(
                        sendRequest(List.of("qa@example.com")),
                        null
                )
        )
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo("QUOTA_EXCEEDED")
                );

        verify(emailSendRateLimiter)
                .refund(any(EmailSendRateLimiter.Lease.class));

        verify(outboxService, never()).enqueue(
                any(),
                any(),
                any(),
                any(),
                any()
        );

        verify(emailMessageRepository, never())
                .save(any());
    }

    @Test
    void concurrentIdempotencyCollisionRefundsRateLimit() {
        EmailMessageEntity existing = EmailMessageEntity.create(
                tenantId,
                "noreply@acme.texto.test",
                List.of("one@example.com"),
                List.of(),
                List.of(),
                List.of(),
                null,
                "Idem",
                null,
                "body",
                null,
                null,
                "race-key",
                Map.of(),
                5,
                null
        );

        when(emailMessageRepository.findByTenantIdAndIdempotencyKey(
                tenantId,
                "race-key"
        ))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(existing)
                );

        when(emailMessageRepository.save(any(EmailMessageEntity.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate idempotency key"
                        )
                );

        var response = emailService.send(
                sendRequest(List.of("one@example.com")),
                "race-key"
        );

        assertThat(response.id())
                .isEqualTo(existing.getId());

        verify(emailSendRateLimiter)
                .acquire(tenantId);

        verify(emailSendRateLimiter)
                .refund(any(EmailSendRateLimiter.Lease.class));

        verify(outboxService, never()).enqueue(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    private static SendEmailRequest sendRequest(List<String> to) {
        return new SendEmailRequest(
                "noreply@acme.texto.test",
                to,
                null,
                null,
                null,
                "Hello",
                null,
                "Body",
                null,
                null,
                null,
                null
        );
    }
}