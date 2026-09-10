package com.texto.emailplatform.plan.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<PlanEntity, UUID> {

    List<PlanEntity> findByActiveTrueOrderBySortOrderAsc();

    Optional<PlanEntity> findByCode(String code);
}
