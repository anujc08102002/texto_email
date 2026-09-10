package com.texto.emailplatform.billing.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderPlanMappingRepository extends JpaRepository<ProviderPlanMappingEntity, UUID> {

    Optional<ProviderPlanMappingEntity> findByProviderAndPlanIdAndActiveTrue(String provider, UUID planId);

    Optional<ProviderPlanMappingEntity> findByProviderAndProviderPlanIdAndActiveTrue(
            String provider,
            String providerPlanId
    );
}
