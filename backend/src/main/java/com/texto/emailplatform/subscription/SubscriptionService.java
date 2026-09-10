package com.texto.emailplatform.subscription;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.plan.PlanCodes;
import com.texto.emailplatform.plan.domain.PlanEntity;
import com.texto.emailplatform.plan.domain.PlanRepository;
import com.texto.emailplatform.subscription.api.SubscriptionResponse;
import com.texto.emailplatform.subscription.domain.SubscriptionEntity;
import com.texto.emailplatform.subscription.domain.SubscriptionRepository;
import com.texto.emailplatform.usage.BillingPeriod;
import com.texto.emailplatform.usage.UsageService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UsageService usageService;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository,
            UsageService usageService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.usageService = usageService;
    }

    /**
     * Puts a freshly registered tenant on the FREE plan for the current calendar month
     * and seeds its usage counters.
     */
    @Transactional
    public SubscriptionEntity createFreeForTenant(UUID tenantId) {
        PlanEntity plan = planRepository.findByCode(PlanCodes.FREE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "PLAN_NOT_FOUND",
                        "The FREE plan is missing from the catalog"
                ));
        BillingPeriod period = BillingPeriod.current();
        SubscriptionEntity subscription = subscriptionRepository.save(SubscriptionEntity.create(
                tenantId,
                plan.getId(),
                SubscriptionStatus.ACTIVE,
                period.startInstant(),
                period.endInstant()
        ));
        usageService.ensurePeriodRows(tenantId);
        return subscription;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrent(UUID tenantId) {
        SubscriptionEntity subscription = findCurrent(tenantId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription exists for this tenant"
                ));
        PlanEntity plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "PLAN_NOT_FOUND",
                        "Subscribed plan is missing from the catalog"
                ));
        return toResponse(subscription, plan);
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionEntity> findCurrent(UUID tenantId) {
        return subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private SubscriptionResponse toResponse(SubscriptionEntity subscription, PlanEntity plan) {
        String pendingPlanCode = null;
        if (subscription.getPendingPlanId() != null) {
            pendingPlanCode = planRepository.findById(subscription.getPendingPlanId())
                    .map(PlanEntity::getCode)
                    .orElse(null);
        }
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getTenantId(),
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getTrialStart(),
                subscription.getTrialEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCancelledAt(),
                subscription.getCreatedAt(),
                subscription.getProvider(),
                subscription.getProviderSubscriptionId(),
                pendingPlanCode,
                subscription.getGracePeriodEndsAt()
        );
    }
}
