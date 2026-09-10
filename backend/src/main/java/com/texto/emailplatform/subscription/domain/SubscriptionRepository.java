package com.texto.emailplatform.subscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<SubscriptionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SubscriptionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<SubscriptionEntity> findByProviderAndProviderSubscriptionId(
            String provider,
            String providerSubscriptionId
    );
}
