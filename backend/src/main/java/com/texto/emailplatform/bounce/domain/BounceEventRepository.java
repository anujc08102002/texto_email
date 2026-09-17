package com.texto.emailplatform.bounce.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BounceEventRepository extends JpaRepository<BounceEventEntity, UUID> {

    Optional<BounceEventEntity> findByTenantIdAndEventHash(UUID tenantId, String eventHash);

    Optional<BounceEventEntity> findByEventHash(String eventHash);

    List<BounceEventEntity> findByTenantIdAndEmailMessageId(UUID tenantId, UUID emailMessageId);

    Optional<BounceEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
