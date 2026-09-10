package com.texto.emailplatform.entitlement;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.entitlement.api.EntitlementsResponse;
import com.texto.emailplatform.plan.FeatureCodes;
import com.texto.emailplatform.plan.domain.FeatureRepository;
import com.texto.emailplatform.plan.domain.PlanEntity;
import com.texto.emailplatform.plan.domain.PlanFeatureEntity;
import com.texto.emailplatform.plan.domain.PlanFeatureRepository;
import com.texto.emailplatform.plan.domain.PlanLimitEntity;
import com.texto.emailplatform.plan.domain.PlanLimitRepository;
import com.texto.emailplatform.plan.domain.PlanRepository;
import com.texto.emailplatform.subscription.SubscriptionStatus;
import com.texto.emailplatform.subscription.domain.SubscriptionEntity;
import com.texto.emailplatform.subscription.domain.SubscriptionRepository;
import com.texto.emailplatform.usage.BillingPeriod;
import com.texto.emailplatform.usage.domain.TenantUsageEntity;
import com.texto.emailplatform.usage.domain.TenantUsageRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "is this tenant allowed to do X" from the plan matrix, never from hardcoded plan conditionals.
 */
@Service
public class EntitlementService {

    /**
     * Features that stay available while a subscription is RESTRICTED (read-only degradation).
     */
    private static final Set<String> READ_ONLY_FEATURES = Set.of(
            FeatureCodes.BASIC_ANALYTICS,
            FeatureCodes.ADVANCED_ANALYTICS,
            FeatureCodes.REPUTATION_DASHBOARD
    );

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PlanLimitRepository planLimitRepository;
    private final TenantUsageRepository tenantUsageRepository;

    public EntitlementService(
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository,
            FeatureRepository featureRepository,
            PlanFeatureRepository planFeatureRepository,
            PlanLimitRepository planLimitRepository,
            TenantUsageRepository tenantUsageRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.featureRepository = featureRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.planLimitRepository = planLimitRepository;
        this.tenantUsageRepository = tenantUsageRepository;
    }

    /**
     * Whether the tenant's plan includes the feature, ignoring subscription state.
     */
    @Transactional(readOnly = true)
    public boolean hasFeature(UUID tenantId, String featureCode) {
        return currentSubscription(tenantId)
                .flatMap(subscription -> featureRepository.findByCode(featureCode)
                        .flatMap(feature -> planFeatureRepository.findByPlanIdAndFeatureId(subscription.getPlanId(), feature.getId())))
                .map(PlanFeatureEntity::isEnabled)
                .orElse(false);
    }

    /**
     * Plan limit for a metric. An empty result means unlimited; a tenant without a subscription gets zero.
     */
    @Transactional(readOnly = true)
    public OptionalLong getLimit(UUID tenantId, String metric) {
        Optional<SubscriptionEntity> subscription = currentSubscription(tenantId);
        if (subscription.isEmpty()) {
            return OptionalLong.of(0L);
        }
        Long limit = planLimitRepository.findByPlanIdAndMetric(subscription.get().getPlanId(), metric)
                .map(PlanLimitEntity::getLimitValue)
                .orElse(null);
        return limit == null ? OptionalLong.empty() : OptionalLong.of(limit);
    }

    @Transactional(readOnly = true)
    public long getCurrentUsage(UUID tenantId, String metric) {
        BillingPeriod period = BillingPeriod.current();
        return tenantUsageRepository.findByTenantIdAndPeriodStartAndMetric(tenantId, period.start(), metric)
                .map(TenantUsageEntity::getUsed)
                .orElse(0L);
    }

    /**
     * Whether the feature can be used right now, taking the subscription state into account.
     */
    @Transactional(readOnly = true)
    public boolean canUseFeature(UUID tenantId, String featureCode) {
        Optional<SubscriptionEntity> subscription = currentSubscription(tenantId);
        if (subscription.isEmpty()) {
            return false;
        }
        String status = subscription.get().getStatus();
        if (SubscriptionStatus.RESTRICTED.equals(status)) {
            return READ_ONLY_FEATURES.contains(featureCode) && hasFeature(tenantId, featureCode);
        }
        if (!isEntitled(subscription.get())) {
            return false;
        }
        return hasFeature(tenantId, featureCode);
    }

    @Transactional(readOnly = true)
    public boolean canConsumeLimit(UUID tenantId, String metric, long amount) {
        OptionalLong limit = getLimit(tenantId, metric);
        if (limit.isEmpty()) {
            return true;
        }
        return getCurrentUsage(tenantId, metric) + amount <= limit.getAsLong();
    }

    @Transactional(readOnly = true)
    public EntitlementsResponse getEntitlements(UUID tenantId) {
        SubscriptionEntity subscription = currentSubscription(tenantId)
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

        Map<UUID, String> featureCodes = new LinkedHashMap<>();
        featureRepository.findAll().forEach(feature -> featureCodes.put(feature.getId(), feature.getCode()));

        boolean blocked = isBlocked(subscription);
        boolean restricted = SubscriptionStatus.RESTRICTED.equals(subscription.getStatus());

        Map<String, Boolean> features = new LinkedHashMap<>();
        planFeatureRepository.findByPlanId(plan.getId()).stream()
                .filter(planFeature -> featureCodes.containsKey(planFeature.getFeatureId()))
                .sorted(Comparator.comparing(planFeature -> featureCodes.get(planFeature.getFeatureId())))
                .forEach(planFeature -> {
                    String code = featureCodes.get(planFeature.getFeatureId());
                    boolean enabled = planFeature.isEnabled()
                            && !blocked
                            && (!restricted || READ_ONLY_FEATURES.contains(code));
                    features.put(code, enabled);
                });

        Map<String, Long> limits = new LinkedHashMap<>();
        planLimitRepository.findByPlanId(plan.getId()).stream()
                .sorted(Comparator.comparing(PlanLimitEntity::getMetric))
                .forEach(limit -> limits.put(limit.getMetric(), limit.getLimitValue()));

        BillingPeriod period = BillingPeriod.current();
        Map<String, Long> usage = new LinkedHashMap<>();
        tenantUsageRepository.findByTenantIdAndPeriodStartOrderByMetricAsc(tenantId, period.start())
                .forEach(entry -> usage.put(entry.getMetric(), entry.getUsed()));

        return new EntitlementsResponse(
                tenantId,
                plan.getCode(),
                plan.getName(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                features,
                limits,
                usage
        );
    }

    private Optional<SubscriptionEntity> currentSubscription(UUID tenantId) {
        return tenantId == null
                ? Optional.empty()
                : subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * CANCELLED subscriptions remain entitled until {@code current_period_end} so cancel-at-period-end
     * and mid-cycle provider cancels keep access through the paid window.
     */
    private static boolean isEntitled(SubscriptionEntity subscription) {
        if (SubscriptionStatus.ENTITLED.contains(subscription.getStatus())) {
            return true;
        }
        return SubscriptionStatus.CANCELLED.equals(subscription.getStatus())
                && subscription.getCurrentPeriodEnd() != null
                && subscription.getCurrentPeriodEnd().isAfter(Instant.now());
    }

    private static boolean isBlocked(SubscriptionEntity subscription) {
        if (isEntitled(subscription)) {
            return false;
        }
        return SubscriptionStatus.BLOCKED.contains(subscription.getStatus());
    }
}
