package com.texto.emailplatform.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.plan.api.PlanDetailResponse;
import com.texto.emailplatform.plan.domain.FeatureEntity;
import com.texto.emailplatform.plan.domain.FeatureRepository;
import com.texto.emailplatform.plan.domain.PlanEntity;
import com.texto.emailplatform.plan.domain.PlanFeatureEntity;
import com.texto.emailplatform.plan.domain.PlanFeatureRepository;
import com.texto.emailplatform.plan.domain.PlanLimitEntity;
import com.texto.emailplatform.plan.domain.PlanLimitRepository;
import com.texto.emailplatform.plan.domain.PlanRepository;
import com.texto.emailplatform.usage.UsageMetrics;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    private static final UUID PLAN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FEATURE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private PlanRepository planRepository;

    @Mock
    private FeatureRepository featureRepository;

    @Mock
    private PlanFeatureRepository planFeatureRepository;

    @Mock
    private PlanLimitRepository planLimitRepository;

    @InjectMocks
    private PlanService planService;

    @Test
    void mapsPersistedPlansWithFeaturesAndLimits() {
        PlanEntity plan = mock(PlanEntity.class);
        when(plan.getId()).thenReturn(PLAN_ID);
        when(plan.getCode()).thenReturn(PlanCodes.FREE);
        when(plan.getName()).thenReturn("Free");
        when(plan.getDescription()).thenReturn("Development starter workspace.");
        when(plan.isActive()).thenReturn(true);
        when(plan.getSortOrder()).thenReturn(10);
        when(planRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(plan));

        FeatureEntity feature = mock(FeatureEntity.class);
        when(feature.getId()).thenReturn(FEATURE_ID);
        when(feature.getCode()).thenReturn(FeatureCodes.API_SENDING);
        when(featureRepository.findAll()).thenReturn(List.of(feature));

        PlanFeatureEntity planFeature = mock(PlanFeatureEntity.class);
        when(planFeature.getFeatureId()).thenReturn(FEATURE_ID);
        when(planFeature.isEnabled()).thenReturn(true);
        when(planFeatureRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(planFeature));

        PlanLimitEntity monthlyEmails = mock(PlanLimitEntity.class);
        when(monthlyEmails.getMetric()).thenReturn(UsageMetrics.MONTHLY_EMAILS);
        when(monthlyEmails.getLimitValue()).thenReturn(500L);
        PlanLimitEntity campaigns = mock(PlanLimitEntity.class);
        when(campaigns.getMetric()).thenReturn(UsageMetrics.CAMPAIGNS);
        when(campaigns.getLimitValue()).thenReturn(null);
        when(planLimitRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(monthlyEmails, campaigns));

        List<PlanDetailResponse> plans = planService.listActive();

        assertThat(plans).hasSize(1);
        PlanDetailResponse response = plans.getFirst();
        assertThat(response.code()).isEqualTo(PlanCodes.FREE);
        assertThat(response.name()).isEqualTo("Free");
        assertThat(response.sortOrder()).isEqualTo(10);
        assertThat(response.features()).containsEntry(FeatureCodes.API_SENDING, true);
        assertThat(response.limits()).containsEntry(UsageMetrics.MONTHLY_EMAILS, 500L);
        assertThat(response.limits()).containsEntry(UsageMetrics.CAMPAIGNS, null);
    }
}
