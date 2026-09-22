package com.texto.emailplatform.complaint.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ComplaintEventRepository
        extends JpaRepository<ComplaintEventEntity, UUID>, JpaSpecificationExecutor<ComplaintEventEntity> {

    Optional<ComplaintEventEntity> findByEventHash(String eventHash);

    Optional<ComplaintEventEntity> findByProviderAndProviderEventIdAndRecipient(
            String provider,
            String providerEventId,
            String recipient
    );

    List<ComplaintEventEntity> findByTenantIdAndEmailMessageId(UUID tenantId, UUID emailMessageId);

    List<ComplaintEventEntity> findByEmailMessageIdOrderByReceivedAtAsc(UUID emailMessageId);

    Optional<ComplaintEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    List<ComplaintEventEntity> findTop20ByOrderByReceivedAtDesc();

    List<ComplaintEventEntity> findTop20ByTenantIdOrderByReceivedAtDesc(UUID tenantId);
}
