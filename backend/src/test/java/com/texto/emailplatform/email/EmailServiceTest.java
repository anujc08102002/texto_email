package com.texto.emailplatform.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.domain.DomainService;
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

    private EmailService emailService;

    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

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
                properties
        );
        when(emailMessageRepository.save(any(EmailMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(entitlementService.hasFeature(tenantId, FeatureCodes.API_SENDING)).thenReturn(true);
        when(entitlementService.canUseFeature(tenantId, FeatureCodes.API_SENDING)).thenReturn(true);
        when(suppressionService.isSuppressed(eq(tenantId), anyString())).thenReturn(false);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendTestQueuesMessageAndEnqueuesOutbox() {
        TenantEntity tenant = TenantEntity.create("Acme", "acme");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        var response = emailService.sendTest(new SendTestEmailRequest("qa@example.com", "Hello", "Body"));

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.recipient()).isEqualTo("qa@example.com");
        verify(usageService).consumeQuota(tenantId, UsageMetrics.MONTHLY_EMAILS, 1);
        verify(domainService).requireVerifiedSender(eq(tenantId), eq("noreply@acme.texto.test"));
        verify(outboxService).enqueue(
                eq(tenantId),
                eq(OutboxEventEntity.AGGREGATE_EMAIL_MESSAGE),
                any(UUID.class),
                eq(OutboxEventEntity.EMAIL_DELIVERY_REQUESTED),
                any(Map.class)
        );
        verify(webhookEventPublisher).publishEmailEvent(eq(WebhookEventTypes.EMAIL_QUEUED), any(EmailMessageEntity.class));
    }

    @Test
    void sendTestMarksSuppressedWithoutQuotaOrOutbox() {
        TenantEntity tenant = TenantEntity.create("Acme", "acme");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(suppressionService.isSuppressed(tenantId, "blocked@example.com")).thenReturn(true);

        var response = emailService.sendTest(new SendTestEmailRequest("blocked@example.com", "Hello", "Body"));

        assertThat(response.status()).isEqualTo("SUPPRESSED");
        verify(usageService, never()).consumeQuota(eq(tenantId), anyString(), anyLong());
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any());
        verify(webhookEventPublisher).publishEmailEvent(eq(WebhookEventTypes.EMAIL_SUPPRESSED), any(EmailMessageEntity.class));
    }

    @Test
    void sendTestPersistsQueuedEntity() {
        TenantEntity tenant = TenantEntity.create("Acme", "acme");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        emailService.sendTest(new SendTestEmailRequest("qa@example.com", "Hello", "Body"));

        ArgumentCaptor<EmailMessageEntity> captor = ArgumentCaptor.forClass(EmailMessageEntity.class);
        verify(emailMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(captor.getValue().getQueuedAt()).isNotNull();
    }
}
