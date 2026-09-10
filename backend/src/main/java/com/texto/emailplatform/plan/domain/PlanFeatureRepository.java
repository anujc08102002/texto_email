package com.texto.emailplatform.plan.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanFeatureRepository extends JpaRepository<PlanFeatureEntity, UUID> {

    List<PlanFeatureEntity> findByPlanId(UUID planId);

    Optional<PlanFeatureEntity> findByPlanIdAndFeatureId(UUID planId, UUID featureId);
}
