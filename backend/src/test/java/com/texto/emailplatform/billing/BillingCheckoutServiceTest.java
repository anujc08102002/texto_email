package com.texto.emailplatform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.billing.api.CheckoutResponse;
import com.texto.emailplatform.billing.domain.ProviderPlanMappingEntity;
import com.texto.emailplatform.billing.domain.ProviderPlanMappingRepository;
import com.texto.emailplatform.billing.spi.BillingProviders;
import com.texto.emailplatform.billing.spi.CreateSubscriptionCommand;
import com.texto.emailplatform.billing.spi.ProviderSubscription;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.plan.PlanCodes;
import com.texto.emailplatform.plan.domain.PlanEntity;
import com.texto.emailplatform.plan.domain.PlanRepository;
import com.texto.emailplatform.subscription.SubscriptionService;
import com.texto.emailplatform.subscription.SubscriptionStatus;
import com.texto.emailplatform.subscription.domain.SubscriptionEntity;
import com.texto.emailplatform.subscription.domain.SubscriptionRepository;
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
class BillingCheckoutServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FREE_PLAN_ID = UUID.fromString("a1000000-0000-4000-8000-000000000001");
    private static final UUID STARTER_PLAN_ID = UUID.fromString("a1000000-0000-4000-8000-000000000002");
    private static final UUID ENTERPRISE_PLAN_ID = UUID.fromString("a1000000-0000-4000-8000-000000000004");

    @Mock
    private BillingProvider billingProvider;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private ProviderPlanMappingRepository providerPlanMappingRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    private EmailPlatformProperties properties;
    private BillingCheckoutService service;

    @BeforeEach
    void setUp() {
        properties = new EmailPlatformProperties();
        properties.getBilling().getRazorpay().setKeyId("rzp_test_xxx");
        service = new BillingCheckoutService(
                billingProvider,
                properties,
                planRepository,
                providerPlanMappingRepository,
                subscriptionRepository,
                subscriptionService
        );
    }

    @Test
    void createCheckoutThrowsWhenProviderNotConfigured() {
        when(billingProvider.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.createCheckout(TENANT_ID, PlanCodes.STARTER, "a@b.com", "User"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("BILLING_NOT_CONFIGURED");
    }

    @Test
    void createCheckoutRejectsFreePlan() {
        when(billingProvider.isConfigured()).thenReturn(true);

        assertThatThrownBy(() -> service.createCheckout(TENANT_ID, PlanCodes.FREE, "a@b.com", "User"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("PLAN_NOT_CHECKOUTABLE");
        verify(billingProvider, never()).createSubscription(any());
    }

    @Test
    void createCheckoutRejectsEnterpriseWithoutMapping() {
        when(billingProvider.isConfigured()).thenReturn(true);
        when(billingProvider.name()).thenReturn(BillingProviders.RAZORPAY);
        PlanEntity enterprise = plan(ENTERPRISE_PLAN_ID, PlanCodes.ENTERPRISE);
        when(planRepository.findByCode(PlanCodes.ENTERPRISE)).thenReturn(Optional.of(enterprise));
        when(providerPlanMappingRepository.findByProviderAndPlanIdAndActiveTrue(
                BillingProviders.RAZORPAY,
                ENTERPRISE_PLAN_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCheckout(TENANT_ID, PlanCodes.ENTERPRISE, "a@b.com", "User"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("PLAN_NOT_CHECKOUTABLE");
    }

    @Test
    void createCheckoutStoresProviderIdsAndPendingPlanWithoutChangingCurrentPlan() {
        when(billingProvider.isConfigured()).thenReturn(true);
        when(billingProvider.name()).thenReturn(BillingProviders.RAZORPAY);

        PlanEntity starter = plan(STARTER_PLAN_ID, PlanCodes.STARTER);
        when(planRepository.findByCode(PlanCodes.STARTER)).thenReturn(Optional.of(starter));

        ProviderPlanMappingEntity mapping = mapping(STARTER_PLAN_ID, "plan_starter_test");
        when(providerPlanMappingRepository.findByProviderAndPlanIdAndActiveTrue(
                BillingProviders.RAZORPAY,
                STARTER_PLAN_ID
        )).thenReturn(Optional.of(mapping));

        SubscriptionEntity subscription = SubscriptionEntity.create(
                TENANT_ID,
                FREE_PLAN_ID,
                SubscriptionStatus.ACTIVE,
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(3600)
        );
        when(subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(TENANT_ID))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderSubscription providerSubscription = new ProviderSubscription(
                "sub_abc",
                "cust_xyz",
                "plan_starter_test",
                "created",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );
        when(billingProvider.createSubscription(any(CreateSubscriptionCommand.class)))
                .thenReturn(providerSubscription);

        CheckoutResponse response = service.createCheckout(TENANT_ID, PlanCodes.STARTER, "owner@texto.test", "Owner");

        assertThat(response.providerSubscriptionId()).isEqualTo("sub_abc");
        assertThat(response.planCode()).isEqualTo(PlanCodes.STARTER);
        assertThat(response.keyId()).isEqualTo("rzp_test_xxx");
        assertThat(subscription.getPlanId()).isEqualTo(FREE_PLAN_ID);
        assertThat(subscription.getPendingPlanId()).isEqualTo(STARTER_PLAN_ID);
        assertThat(subscription.getProviderSubscriptionId()).isEqualTo("sub_abc");
        assertThat(subscription.getProviderCustomerId()).isEqualTo("cust_xyz");

        ArgumentCaptor<CreateSubscriptionCommand> captor = ArgumentCaptor.forClass(CreateSubscriptionCommand.class);
        verify(billingProvider).createSubscription(captor.capture());
        assertThat(captor.getValue().providerPlanId()).isEqualTo("plan_starter_test");
    }

    private static PlanEntity plan(UUID id, String code) {
        PlanEntity plan = org.mockito.Mockito.mock(PlanEntity.class);
        org.mockito.Mockito.lenient().when(plan.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(plan.getCode()).thenReturn(code);
        org.mockito.Mockito.lenient().when(plan.isActive()).thenReturn(true);
        return plan;
    }

    private static ProviderPlanMappingEntity mapping(UUID planId, String providerPlanId) {
        ProviderPlanMappingEntity mapping = org.mockito.Mockito.mock(ProviderPlanMappingEntity.class);
        org.mockito.Mockito.lenient().when(mapping.getPlanId()).thenReturn(planId);
        org.mockito.Mockito.lenient().when(mapping.getProviderPlanId()).thenReturn(providerPlanId);
        org.mockito.Mockito.lenient().when(mapping.isActive()).thenReturn(true);
        return mapping;
    }
}
