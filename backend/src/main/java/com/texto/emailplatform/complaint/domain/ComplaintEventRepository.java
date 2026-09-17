package com.texto.emailplatform.complaint.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintEventRepository extends JpaRepository<ComplaintEventEntity, UUID> {

    Optional<ComplaintEventEntity> findByEventHash(String eventHash);

    List<ComplaintEventEntity> findByTenantIdAndEmailMessageId(UUID tenantId, UUID emailMessageId);

    Optional<ComplaintEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
