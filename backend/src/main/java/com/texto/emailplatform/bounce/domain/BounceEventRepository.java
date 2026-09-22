package com.texto.emailplatform.bounce.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BounceEventRepository
        extends JpaRepository<BounceEventEntity, UUID>, JpaSpecificationExecutor<BounceEventEntity> {

    Optional<BounceEventEntity> findByTenantIdAndEventHash(UUID tenantId, String eventHash);

    Optional<BounceEventEntity> findByEventHash(String eventHash);

    Optional<BounceEventEntity> findByProviderAndProviderEventIdAndFinalRecipient(
            String provider,
            String providerEventId,
            String finalRecipient
    );

    List<BounceEventEntity> findByTenantIdAndEmailMessageId(UUID tenantId, UUID emailMessageId);

    List<BounceEventEntity> findByEmailMessageIdOrderByReceivedAtAsc(UUID emailMessageId);

    Optional<BounceEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<BounceEventEntity> findTop20ByOrderByReceivedAtDesc();

    List<BounceEventEntity> findTop20ByTenantIdOrderByReceivedAtDesc(UUID tenantId);
}
