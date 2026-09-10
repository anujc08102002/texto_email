package com.texto.emailplatform.webhook;

import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.webhook.domain.WebhookConfigEntity;
import com.texto.emailplatform.webhook.domain.WebhookConfigRepository;
import com.texto.emailplatform.webhook.domain.WebhookEventEntity;
import com.texto.emailplatform.webhook.domain.WebhookEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookEventPublisher {

    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public WebhookEventPublisher(
            WebhookConfigRepository webhookConfigRepository,
            WebhookEventRepository webhookEventRepository,
            RabbitTemplate rabbitTemplate
    ) {
        this.webhookConfigRepository = webhookConfigRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void publishEmailEvent(String eventType, EmailMessageEntity message) {
        List<WebhookConfigEntity> configs = webhookConfigRepository.findByTenantIdAndStatus(
                message.getTenantId(),
                WebhookConfigEntity.STATUS_ACTIVE
        );
        for (WebhookConfigEntity config : configs) {
            if (!config.subscribesTo(eventType)) {
                continue;
            }
            publish(config, eventType, buildEmailPayload(eventType, message), message.getId());
        }
    }

    @Transactional
    public WebhookEventEntity publish(
            WebhookConfigEntity config,
            String eventType,
            Map<String, Object> data,
            UUID sourceMessageId
    ) {
        if (sourceMessageId != null) {
            var existing = webhookEventRepository.findByWebhookConfigIdAndEventTypeAndSourceMessageId(
                    config.getId(),
                    eventType,
                    sourceMessageId
            );
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        UUID eventId = UUID.randomUUID();
        envelope.put("id", eventId.toString());
        envelope.put("type", eventType);
        envelope.put("created_at", Instant.now().toString());
        envelope.put("tenant_id", config.getTenantId().toString());
        envelope.put("data", data);

        WebhookEventEntity event = WebhookEventEntity.create(
                config.getTenantId(),
                config.getId(),
                eventType,
                envelope,
                sourceMessageId
        );
        // Keep envelope id aligned with persistence id.
        envelope.put("id", event.getId().toString());
        try {
            event = webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException exception) {
            if (sourceMessageId == null) {
                throw exception;
            }
            return webhookEventRepository
                    .findByWebhookConfigIdAndEventTypeAndSourceMessageId(config.getId(), eventType, sourceMessageId)
                    .orElseThrow(() -> exception);
        }
        rabbitTemplate.convertAndSend(
                WebhookQueues.WEBHOOK_EXCHANGE,
                WebhookQueues.DELIVERY_ROUTING_KEY,
                event.getId().toString()
        );
        return event;
    }

    private static Map<String, Object> buildEmailPayload(String eventType, EmailMessageEntity message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_id", message.getId().toString());
        data.put("recipient", message.getRecipient());
        data.put("status", message.getStatus());
        data.put("subject", message.getSubject());
        if (message.getStatusReason() != null) {
            data.put("status_reason", message.getStatusReason());
        }
        data.put("event", eventType);
        return data;
    }
}
