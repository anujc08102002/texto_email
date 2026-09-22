package com.texto.emailplatform.email.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeliveryAttemptRepository
        extends JpaRepository<DeliveryAttemptEntity, UUID>, JpaSpecificationExecutor<DeliveryAttemptEntity> {

    List<DeliveryAttemptEntity> findByMessageIdAndTenantIdOrderByAttemptNumberAsc(UUID messageId, UUID tenantId);

    List<DeliveryAttemptEntity> findByMessageIdOrderByAttemptNumberAsc(UUID messageId);
}
