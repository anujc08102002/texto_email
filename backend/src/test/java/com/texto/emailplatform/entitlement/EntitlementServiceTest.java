package com.texto.emailplatform.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.plan.FeatureCodes;
import com.texto.emailplatform.plan.domain.FeatureEntity;
import com.texto.emailplatform.plan.domain.FeatureRepository;
import com.texto.emailplatform.plan.domain.PlanFeatureEntity;
import com.texto.emailplatform.plan.domain.PlanFeatureRepository;
import com.texto.emailplatform.plan.domain.PlanLimitEntity;
import com.texto.emailplatform.plan.domain.PlanLimitRepository;
import com.texto.emailplatform.plan.domain.PlanRepository;
import com.texto.emailplatform.subscription.SubscriptionStatus;
import com.texto.emailplatform.subscription.domain.SubscriptionEntity;
import com.texto.emailplatform.subscription.domain.SubscriptionRepository;
import com.texto.emailplatform.usage.BillingPeriod;
import com.texto.emailplatform.usage.UsageMetrics;
import com.texto.emailplatform.usage.domain.TenantUsageEntity;
import com.texto.emailplatform.usage.domain.TenantUsageRepository;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID FEATURE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private FeatureRepository featureRepository;

    @Mock
    private PlanFeatureRepository planFeatureRepository;

    @Mock
    private PlanLimitRepository planLimitRepository;

    @Mock
    private TenantUsageRepository tenantUsageRepository;

    @InjectMocks
    private EntitlementService entitlementService;

    @Test
    void hasFeatureReadsThePlanMatrix() {
        subscription(SubscriptionStatus.ACTIVE);
        planFeature(FeatureCodes.API_SENDING, true);

        assertThat(entitlementService.hasFeature(TENANT_ID, FeatureCodes.API_SENDING)).isTrue();
    }

    @Test
    void hasFeatureIsFalseWhenThePlanDisablesIt() {
        subscription(SubscriptionStatus.ACTIVE);
        planFeature(FeatureCodes.CAMPAIGNS, false);

        assertThat(entitlementService.hasFeature(TENANT_ID, FeatureCodes.CAMPAIGNS)).isFalse();
    }

    @Test
    void tenantWithoutSubscriptionHasNothing() {
        when(subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(TENANT_ID)).thenReturn(Optional.empty());

        assertThat(entitlementService.hasFeature(TENANT_ID, FeatureCodes.API_SENDING)).isFalse();
        assertThat(entitlementService.canUseFeature(TENANT_ID, FeatureCodes.API_SENDING)).isFalse();
        assertThat(entitlementService.getLimit(TENANT_ID, UsageMetrics.MONTHLY_EMAILS)).hasValue(0L);
    }

    @Test
    void suspendedSubscriptionBlocksFeatures() {
        subscription(SubscriptionStatus.SUSPENDED);

        assertThat(entitlementService.canUseFeature(TENANT_ID, FeatureCodes.API_SENDING)).isFalse();
    }

    @Test
    void restrictedSubscriptionKeepsReadOnlyFeaturesOnly() {
        subscription(SubscriptionStatus.RESTRICTED);
        planFeature(FeatureCodes.BASIC_ANALYTICS, true);

        assertThat(entitlementService.canUseFeature(TENANT_ID, FeatureCodes.BASIC_ANALYTICS)).isTrue();
        assertThat(entitlementService.canUseFeature(TENANT_ID, FeatureCodes.API_SENDING)).isFalse();
    }

    @Test
    void pastDueSubscriptionStillAllowsPlanFeatures() {
        subscription(SubscriptionStatus.PAST_DUE);
        planFeature(FeatureCodes.API_SENDING, true);

        assertThat(entitlementService.canUseFeature(TENANT_ID, FeatureCodes.API_SENDING)).isTrue();
    }

    @Test
    void cancelledSubscriptionStillAllowsFeaturesUntilPeriodEnd() {
        SubscriptionEntity subscription = mock(SubscriptionEntity.class);
        lenient().when(subscription.getPlanId()).thenReturn(PLAN_ID);
        lenient().when(subscription.getStatus()).thenReturn(SubscriptionStatus.CANCELLED);
        lenient().when(subscription.getCurrentPeriodEnd()).thenReturn(java.time.Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(TENANT_ID))
                .thenReturn(Optional.of(subscription));
        planFeature(FeatureCodes.API_SENDING, true);

        assertThat(entitlementService.canUseFeature(TENANT_ID, FeatureCodes.API_SENDING)).isTrue();
    }

    @Test
    void cancelledSubscriptionBlocksFeaturesAfterPeriodEnd() {
        SubscriptionEntity subscription = mock(SubscriptionEntity.class);
        lenient().when(subscription.getPlanId()).thenReturn(PLAN_ID);
        lenient().when(subscription.getStatus()).thenReturn(SubscriptionStatus.CANCELLED);
        lenient().when(subscription.getCurrentPeriodEnd()).thenReturn(java.time.Instant.now().minusSeconds(60));
        when(subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(TENANT_ID))
                .thenReturn(Optional.of(subscription));

        assertThat(entitlementService.canUseFeature(TENANT_ID, FeatureCodes.API_SENDING)).isFalse();
    }

    @Test
    void missingLimitRowMeansUnlimited() {
        subscription(SubscriptionStatus.ACTIVE);
        when(planLimitRepository.findByPlanIdAndMetric(PLAN_ID, UsageMetrics.TEMPLATES)).thenReturn(Optional.empty());

        assertThat(entitlementService.getLimit(TENANT_ID, UsageMetrics.TEMPLATES)).isEmpty();
        assertThat(entitlementService.canConsumeLimit(TENANT_ID, UsageMetrics.TEMPLATES, 1_000)).isTrue();
    }

    @Test
    void nullLimitValueMeansUnlimited() {
        subscription(SubscriptionStatus.ACTIVE);
        limit(UsageMetrics.CAMPAIGNS, null);

        assertThat(entitlementService.getLimit(TENANT_ID, UsageMetrics.CAMPAIGNS)).isEqualTo(OptionalLong.empty());
    }

    @Test
    void canConsumeLimitComparesUsageAgainstThePlanLimit() {
        subscription(SubscriptionStatus.ACTIVE);
        limit(UsageMetrics.MONTHLY_EMAILS, 500L);
        usage(UsageMetrics.MONTHLY_EMAILS, 499L);

        assertThat(entitlementService.getCurrentUsage(TENANT_ID, UsageMetrics.MONTHLY_EMAILS)).isEqualTo(499L);
        assertThat(entitlementService.canConsumeLimit(TENANT_ID, UsageMetrics.MONTHLY_EMAILS, 1)).isTrue();
        assertThat(entitlementService.canConsumeLimit(TENANT_ID, UsageMetrics.MONTHLY_EMAILS, 2)).isFalse();
    }

    private void subscription(String status) {
        SubscriptionEntity subscription = mock(SubscriptionEntity.class);
        lenient().when(subscription.getPlanId()).thenReturn(PLAN_ID);
        lenient().when(subscription.getStatus()).thenReturn(status);
        when(subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(TENANT_ID))
                .thenReturn(Optional.of(subscription));
    }

    private void planFeature(String featureCode, boolean enabled) {
        FeatureEntity feature = mock(FeatureEntity.class);
        lenient().when(feature.getId()).thenReturn(FEATURE_ID);
        lenient().when(featureRepository.findByCode(featureCode)).thenReturn(Optional.of(feature));

        PlanFeatureEntity planFeature = mock(PlanFeatureEntity.class);
        lenient().when(planFeature.isEnabled()).thenReturn(enabled);
        lenient().when(planFeatureRepository.findByPlanIdAndFeatureId(PLAN_ID, FEATURE_ID))
                .thenReturn(Optional.of(planFeature));
    }

    private void limit(String metric, Long value) {
        PlanLimitEntity planLimit = mock(PlanLimitEntity.class);
        lenient().when(planLimit.getLimitValue()).thenReturn(value);
        when(planLimitRepository.findByPlanIdAndMetric(PLAN_ID, metric)).thenReturn(Optional.of(planLimit));
    }

    private void usage(String metric, long used) {
        TenantUsageEntity entity = mock(TenantUsageEntity.class);
        lenient().when(entity.getUsed()).thenReturn(used);
        when(tenantUsageRepository.findByTenantIdAndPeriodStartAndMetric(
                TENANT_ID,
                BillingPeriod.current().start(),
                metric
        )).thenReturn(Optional.of(entity));
    }
}
