package com.texto.emailplatform.plan.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanLimitRepository extends JpaRepository<PlanLimitEntity, UUID> {

    List<PlanLimitEntity> findByPlanId(UUID planId);

    Optional<PlanLimitEntity> findByPlanIdAndMetric(UUID planId, String metric);
}
