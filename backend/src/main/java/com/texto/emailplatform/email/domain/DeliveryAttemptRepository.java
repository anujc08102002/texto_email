package com.texto.emailplatform.email.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {

    List<DeliveryAttemptEntity> findByMessageIdAndTenantIdOrderByAttemptNumberAsc(UUID messageId, UUID tenantId);
}
