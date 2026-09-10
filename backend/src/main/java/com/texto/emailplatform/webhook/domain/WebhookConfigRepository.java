package com.texto.emailplatform.webhook.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookConfigRepository extends JpaRepository<WebhookConfigEntity, UUID> {

    List<WebhookConfigEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<WebhookConfigEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WebhookConfigEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
