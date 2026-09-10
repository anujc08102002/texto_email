package com.texto.emailplatform.plan;

import com.texto.emailplatform.plan.api.PlanDetailResponse;
import com.texto.emailplatform.plan.domain.FeatureEntity;
import com.texto.emailplatform.plan.domain.FeatureRepository;
import com.texto.emailplatform.plan.domain.PlanEntity;
import com.texto.emailplatform.plan.domain.PlanFeatureEntity;
import com.texto.emailplatform.plan.domain.PlanFeatureRepository;
import com.texto.emailplatform.plan.domain.PlanLimitEntity;
import com.texto.emailplatform.plan.domain.PlanLimitRepository;
import com.texto.emailplatform.plan.domain.PlanRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PlanLimitRepository planLimitRepository;

    public PlanService(
            PlanRepository planRepository,
            FeatureRepository featureRepository,
            PlanFeatureRepository planFeatureRepository,
            PlanLimitRepository planLimitRepository
    ) {
        this.planRepository = planRepository;
        this.featureRepository = featureRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.planLimitRepository = planLimitRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanDetailResponse> listActive() {
        Map<UUID, String> featureCodes = featureRepository.findAll().stream()
                .collect(Collectors.toMap(FeatureEntity::getId, FeatureEntity::getCode));
        return planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(plan -> toResponse(plan, featureCodes))
                .toList();
    }

    private PlanDetailResponse toResponse(PlanEntity plan, Map<UUID, String> featureCodes) {
        return new PlanDetailResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.isActive(),
                plan.getSortOrder(),
                features(plan.getId(), featureCodes),
                limits(plan.getId())
        );
    }

    private Map<String, Boolean> features(UUID planId, Map<UUID, String> featureCodes) {
        Map<String, Boolean> features = new LinkedHashMap<>();
        planFeatureRepository.findByPlanId(planId).stream()
                .filter(planFeature -> featureCodes.containsKey(planFeature.getFeatureId()))
                .sorted(Comparator.comparing(planFeature -> featureCodes.get(planFeature.getFeatureId())))
                .forEach(planFeature -> features.put(featureCodes.get(planFeature.getFeatureId()), planFeature.isEnabled()));
        return features;
    }

    private Map<String, Long> limits(UUID planId) {
        Map<String, Long> limits = new LinkedHashMap<>();
        planLimitRepository.findByPlanId(planId).stream()
                .sorted(Comparator.comparing(PlanLimitEntity::getMetric))
                .forEach(limit -> limits.put(limit.getMetric(), limit.getLimitValue()));
        return limits;
    }
}
