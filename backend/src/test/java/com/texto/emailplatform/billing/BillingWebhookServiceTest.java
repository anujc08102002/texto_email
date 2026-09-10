package com.texto.emailplatform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.texto.emailplatform.billing.domain.BillingEventEntity;
import com.texto.emailplatform.billing.domain.BillingEventRepository;
import com.texto.emailplatform.billing.spi.BillingProviders;
import com.texto.emailplatform.billing.spi.ProviderWebhookEvent;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.subscription.SubscriptionStatus;
import com.texto.emailplatform.subscription.domain.SubscriptionEntity;
import com.texto.emailplatform.subscription.domain.SubscriptionRepository;
import com.texto.emailplatform.usage.UsageService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingWebhookServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PENDING_PLAN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private BillingProvider billingProvider;

    @Mock
    private BillingEventRepository billingEventRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UsageService usageService;

    private BillingWebhookService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getBilling().setGracePeriodDays(3);
        service = new BillingWebhookService(
                billingProvider,
                billingEventRepository,
                subscriptionRepository,
                usageService,
                properties,
                objectMapper
        );
        org.mockito.Mockito.lenient().when(billingProvider.name()).thenReturn(BillingProviders.RAZORPAY);
    }

    @Test
    void rejectsInvalidSignature() {
        when(billingProvider.verifyWebhook("{}", "bad")).thenReturn(false);

        assertThatThrownBy(() -> service.handleRazorpayWebhook("{}", "bad"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("BILLING_WEBHOOK_INVALID_SIGNATURE");
    }

    @Test
    void ignoresDuplicateProviderEventIds() {
        ProviderWebhookEvent event = sampleEvent("evt_dup", "subscription.activated");
        when(billingEventRepository.existsByProviderAndProviderEventId(BillingProviders.RAZORPAY, "evt_dup"))
                .thenReturn(true);

        service.processEvent(event);

        verify(subscriptionRepository, never()).save(any());
        verify(billingEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void activatesPendingPlanOnSubscriptionActivated() {
        ProviderWebhookEvent event = sampleEvent("evt_act", "subscription.activated");
        when(billingEventRepository.existsByProviderAndProviderEventId(BillingProviders.RAZORPAY, "evt_act"))
                .thenReturn(false);
        when(billingEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(billingEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity subscription = SubscriptionEntity.create(
                TENANT_ID,
                PLAN_ID,
                SubscriptionStatus.ACTIVE,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );
        subscription.attachProvider(BillingProviders.RAZORPAY, "cust_1", "sub_123", "created");
        subscription.setPendingPlan(PENDING_PLAN_ID);
        when(subscriptionRepository.findByProviderAndProviderSubscriptionId(BillingProviders.RAZORPAY, "sub_123"))
                .thenReturn(Optional.of(subscription));

        service.processEvent(event);

        assertThat(subscription.getPlanId()).isEqualTo(PENDING_PLAN_ID);
        assertThat(subscription.getPendingPlanId()).isNull();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(usageService).ensurePeriodRows(TENANT_ID);

        ArgumentCaptor<BillingEventEntity> captor = ArgumentCaptor.forClass(BillingEventEntity.class);
        verify(billingEventRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().stream().anyMatch(e ->
                BillingEventEntity.STATUS_PROCESSED.equals(e.getProcessingStatus()))).isTrue();
    }

    @Test
    void paymentFailedMarksPastDueAndSetsGraceWindow() {
        ProviderWebhookEvent event = sampleEvent("evt_fail", "payment.failed");
        when(billingEventRepository.existsByProviderAndProviderEventId(BillingProviders.RAZORPAY, "evt_fail"))
                .thenReturn(false);
        when(billingEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(billingEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity subscription = SubscriptionEntity.create(
                TENANT_ID,
                PLAN_ID,
                SubscriptionStatus.ACTIVE,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );
        subscription.attachProvider(BillingProviders.RAZORPAY, "cust_1", "sub_123", "active");
        when(subscriptionRepository.findByProviderAndProviderSubscriptionId(eq(BillingProviders.RAZORPAY), eq("sub_123")))
                .thenReturn(Optional.of(subscription));

        service.processEvent(event);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getGracePeriodEndsAt()).isNotNull();
    }

    @Test
    void ignoresOutOfOrderEventOlderThanLastApplied() {
        Instant now = Instant.now();
        // A stale payment.failed that was delivered late, after a newer event already advanced state.
        ProviderWebhookEvent stale = sampleEvent("evt_stale", "payment.failed", now.minusSeconds(120));
        when(billingEventRepository.existsByProviderAndProviderEventId(BillingProviders.RAZORPAY, "evt_stale"))
                .thenReturn(false);
        when(billingEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(billingEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity subscription = SubscriptionEntity.create(
                TENANT_ID,
                PLAN_ID,
                SubscriptionStatus.ACTIVE,
                now.minusSeconds(60),
                now.plusSeconds(3600)
        );
        subscription.attachProvider(BillingProviders.RAZORPAY, "cust_1", "sub_123", "active");
        subscription.recordBillingEventAt(now); // newer event already applied
        when(subscriptionRepository.findByProviderAndProviderSubscriptionId(BillingProviders.RAZORPAY, "sub_123"))
                .thenReturn(Optional.of(subscription));

        service.processEvent(stale);

        // Stale event must not change subscription state or persist a subscription update.
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getGracePeriodEndsAt()).isNull();
        verify(subscriptionRepository, never()).save(any());
    }

    private ProviderWebhookEvent sampleEvent(String eventId, String type) {
        return sampleEvent(eventId, type, Instant.now());
    }

    private ProviderWebhookEvent sampleEvent(String eventId, String type, Instant createdAt) {
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("event", type);
        boolean activation = "subscription.activated".equals(type)
                || "subscription.authenticated".equals(type)
                || "subscription.charged".equals(type);
        boolean paymentFailure = "payment.failed".equals(type);
        String suggested = activation
                ? SubscriptionStatus.ACTIVE
                : paymentFailure ? SubscriptionStatus.PAST_DUE : null;
        return new ProviderWebhookEvent(
                eventId,
                type,
                "sub_123",
                "cust_1",
                null,
                "active",
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(3600),
                suggested,
                activation,
                paymentFailure,
                createdAt,
                raw
        );
    }
}
