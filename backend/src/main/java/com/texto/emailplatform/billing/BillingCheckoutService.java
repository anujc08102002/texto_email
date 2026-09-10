package com.texto.emailplatform.billing;

import com.texto.emailplatform.billing.api.BillingConfigResponse;
import com.texto.emailplatform.billing.api.CheckoutResponse;
import com.texto.emailplatform.billing.domain.ProviderPlanMappingEntity;
import com.texto.emailplatform.billing.domain.ProviderPlanMappingRepository;
import com.texto.emailplatform.billing.spi.BillingProviders;
import com.texto.emailplatform.billing.spi.CancelSubscriptionCommand;
import com.texto.emailplatform.billing.spi.CreateSubscriptionCommand;
import com.texto.emailplatform.billing.spi.ProviderSubscription;
import com.texto.emailplatform.billing.spi.UpdateSubscriptionCommand;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.plan.PlanCodes;
import com.texto.emailplatform.plan.domain.PlanEntity;
import com.texto.emailplatform.plan.domain.PlanRepository;
import com.texto.emailplatform.subscription.SubscriptionStatus;
import com.texto.emailplatform.subscription.api.SubscriptionResponse;
import com.texto.emailplatform.subscription.domain.SubscriptionEntity;
import com.texto.emailplatform.subscription.domain.SubscriptionRepository;
import com.texto.emailplatform.subscription.SubscriptionService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingCheckoutService {

    private static final Set<String> CHECKOUTABLE_STATUSES = Set.of(
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.GRACE_PERIOD
    );

    private final BillingProvider billingProvider;
    private final EmailPlatformProperties properties;
    private final PlanRepository planRepository;
    private final ProviderPlanMappingRepository providerPlanMappingRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    public BillingCheckoutService(
            BillingProvider billingProvider,
            EmailPlatformProperties properties,
            PlanRepository planRepository,
            ProviderPlanMappingRepository providerPlanMappingRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionService subscriptionService
    ) {
        this.billingProvider = billingProvider;
        this.properties = properties;
        this.planRepository = planRepository;
        this.providerPlanMappingRepository = providerPlanMappingRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
    }

    @Transactional(readOnly = true)
    public BillingConfigResponse getConfig() {
        boolean configured = billingProvider.isConfigured();
        String keyId = null;
        if (configured && BillingProviders.RAZORPAY.equalsIgnoreCase(billingProvider.name())) {
            String configuredKeyId = properties.getBilling().getRazorpay().getKeyId();
            keyId = configuredKeyId == null || configuredKeyId.isBlank() ? null : configuredKeyId;
        }
        return new BillingConfigResponse(billingProvider.name(), configured, keyId);
    }

    @Transactional
    public CheckoutResponse createCheckout(UUID tenantId, String planCode, String actorEmail, String actorName) {
        requireConfigured();
        PlanEntity plan = requireCheckoutablePlan(planCode);
        ProviderPlanMappingEntity mapping = requireActiveMapping(plan);
        SubscriptionEntity subscription = requireCurrentSubscription(tenantId);
        if (!CHECKOUTABLE_STATUSES.contains(subscription.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "SUBSCRIPTION_NOT_CHECKOUTABLE",
                    "Current subscription state does not allow checkout"
            );
        }
        if (plan.getId().equals(subscription.getPlanId()) && subscription.getPendingPlanId() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "PLAN_ALREADY_ACTIVE",
                    "Tenant is already on the requested plan"
            );
        }

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("tenant_id", tenantId.toString());
        notes.put("plan_code", plan.getCode());

        ProviderSubscription providerSubscription = billingProvider.createSubscription(
                new CreateSubscriptionCommand(
                        tenantId,
                        plan.getCode(),
                        mapping.getProviderPlanId(),
                        actorEmail,
                        actorName,
                        null,
                        notes
                )
        );

        subscription.attachProvider(
                billingProvider.name(),
                providerSubscription.providerCustomerId(),
                providerSubscription.providerSubscriptionId(),
                providerSubscription.providerStatus()
        );
        subscription.setPendingPlan(plan.getId());
        if (providerSubscription.currentStart() != null || providerSubscription.currentEnd() != null) {
            subscription.applyPeriod(providerSubscription.currentStart(), providerSubscription.currentEnd());
        }
        subscriptionRepository.save(subscription);

        String keyId = properties.getBilling().getRazorpay().getKeyId();
        return new CheckoutResponse(
                keyId == null || keyId.isBlank() ? null : keyId,
                billingProvider.name(),
                subscription.getId(),
                providerSubscription.providerSubscriptionId(),
                plan.getCode(),
                subscription.getStatus()
        );
    }

    @Transactional
    public SubscriptionResponse cancel(UUID tenantId, boolean atPeriodEnd) {
        requireConfigured();
        SubscriptionEntity subscription = requireProviderSubscription(tenantId);
        ProviderSubscription providerSubscription = billingProvider.cancelSubscription(
                new CancelSubscriptionCommand(subscription.getProviderSubscriptionId(), atPeriodEnd)
        );
        subscription.updateProviderStatus(providerSubscription.providerStatus());
        subscription.cancel(atPeriodEnd);
        if (providerSubscription.currentStart() != null || providerSubscription.currentEnd() != null) {
            subscription.applyPeriod(providerSubscription.currentStart(), providerSubscription.currentEnd());
        }
        subscriptionRepository.save(subscription);
        return subscriptionService.getCurrent(tenantId);
    }

    @Transactional
    public SubscriptionResponse pause(UUID tenantId) {
        requireConfigured();
        SubscriptionEntity subscription = requireProviderSubscription(tenantId);
        ProviderSubscription providerSubscription = billingProvider.pauseSubscription(
                subscription.getProviderSubscriptionId()
        );
        subscription.updateProviderStatus(providerSubscription.providerStatus());
        subscription.changeStatus(SubscriptionStatus.SUSPENDED);
        subscriptionRepository.save(subscription);
        return subscriptionService.getCurrent(tenantId);
    }

    @Transactional
    public SubscriptionResponse resume(UUID tenantId) {
        requireConfigured();
        SubscriptionEntity subscription = requireProviderSubscription(tenantId);
        ProviderSubscription providerSubscription = billingProvider.resumeSubscription(
                subscription.getProviderSubscriptionId()
        );
        subscription.updateProviderStatus(providerSubscription.providerStatus());
        subscription.changeStatus(SubscriptionStatus.ACTIVE);
        subscription.clearGrace();
        subscriptionRepository.save(subscription);
        return subscriptionService.getCurrent(tenantId);
    }

    @Transactional
    public SubscriptionResponse changePlan(UUID tenantId, String planCode) {
        requireConfigured();
        PlanEntity plan = requireCheckoutablePlan(planCode);
        ProviderPlanMappingEntity mapping = requireActiveMapping(plan);
        SubscriptionEntity subscription = requireProviderSubscription(tenantId);
        if (plan.getId().equals(subscription.getPlanId()) && subscription.getPendingPlanId() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "PLAN_ALREADY_ACTIVE",
                    "Tenant is already on the requested plan"
            );
        }

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("tenant_id", tenantId.toString());
        notes.put("plan_code", plan.getCode());

        ProviderSubscription providerSubscription = billingProvider.updateSubscription(
                new UpdateSubscriptionCommand(
                        subscription.getProviderSubscriptionId(),
                        mapping.getProviderPlanId(),
                        notes
                )
        );
        subscription.updateProviderStatus(providerSubscription.providerStatus());
        subscription.setPendingPlan(plan.getId());
        if (providerSubscription.currentStart() != null || providerSubscription.currentEnd() != null) {
            subscription.applyPeriod(providerSubscription.currentStart(), providerSubscription.currentEnd());
        }
        subscriptionRepository.save(subscription);
        return subscriptionService.getCurrent(tenantId);
    }

    private void requireConfigured() {
        if (!billingProvider.isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "BILLING_NOT_CONFIGURED",
                    "Billing provider is not configured"
            );
        }
    }

    private PlanEntity requireCheckoutablePlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "PLAN_CODE_REQUIRED", "planCode is required");
        }
        if (PlanCodes.FREE.equalsIgnoreCase(planCode.trim())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST.value(),
                    "PLAN_NOT_CHECKOUTABLE",
                    "FREE plan does not require billing checkout"
            );
        }
        PlanEntity plan = planRepository.findByCode(planCode.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "PLAN_NOT_FOUND",
                        "Plan was not found"
                ));
        if (!plan.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "PLAN_INACTIVE", "Plan is not active");
        }
        return plan;
    }

    private ProviderPlanMappingEntity requireActiveMapping(PlanEntity plan) {
        return providerPlanMappingRepository
                .findByProviderAndPlanIdAndActiveTrue(billingProvider.name(), plan.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST.value(),
                        "PLAN_NOT_CHECKOUTABLE",
                        "Plan is not available for self-serve checkout"
                ));
    }

    private SubscriptionEntity requireCurrentSubscription(UUID tenantId) {
        return subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription exists for this tenant"
                ));
    }

    private SubscriptionEntity requireProviderSubscription(UUID tenantId) {
        SubscriptionEntity subscription = requireCurrentSubscription(tenantId);
        if (subscription.getProviderSubscriptionId() == null || subscription.getProviderSubscriptionId().isBlank()) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "PROVIDER_SUBSCRIPTION_MISSING",
                    "No provider subscription is attached to this tenant"
            );
        }
        return subscription;
    }
}
