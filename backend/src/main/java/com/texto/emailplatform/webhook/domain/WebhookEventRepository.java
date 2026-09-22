package com.texto.emailplatform.webhook.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebhookEventRepository
        extends JpaRepository<WebhookEventEntity, UUID>, JpaSpecificationExecutor<WebhookEventEntity> {

    List<WebhookEventEntity> findByWebhookConfigIdAndTenantIdOrderByCreatedAtDesc(UUID webhookConfigId, UUID tenantId);

    Optional<WebhookEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<WebhookEventEntity> findByWebhookConfigIdAndEventTypeAndSourceMessageId(
            UUID webhookConfigId,
            String eventType,
            UUID sourceMessageId
    );

    List<WebhookEventEntity> findByStatusAndNextAttemptAtLessThanEqual(String status, Instant nextAttemptAt);
}
