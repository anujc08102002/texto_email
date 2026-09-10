package com.texto.emailplatform.outbox;

import com.texto.emailplatform.queue.EmailQueuePublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final EmailQueuePublisher emailQueuePublisher;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, EmailQueuePublisher emailQueuePublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.emailQueuePublisher = emailQueuePublisher;
    }

    @Scheduled(fixedDelayString = "${email-platform.email.outbox-poll-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> events = outboxEventRepository.findUnpublished(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEventEntity event : events) {
            try {
                publishEvent(event);
                event.markPublished();
                outboxEventRepository.save(event);
            } catch (Exception exception) {
                log.warn("Failed to publish outbox event {}", event.getId());
                event.recordPublishFailure(exception.getMessage());
                outboxEventRepository.save(event);
            }
        }
    }

    private void publishEvent(OutboxEventEntity event) {
        if (OutboxEventEntity.EMAIL_DELIVERY_REQUESTED.equals(event.getEventType())) {
            Map<String, Object> payload = event.getPayload();
            UUID messageId = UUID.fromString(String.valueOf(payload.get("messageId")));
            UUID tenantId = UUID.fromString(String.valueOf(payload.get("tenantId")));
            int attempt = payload.get("attempt") instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(payload.get("attempt")));
            emailQueuePublisher.publishDelivery(messageId, tenantId, attempt);
            return;
        }
        log.warn("Unknown outbox event type {}", event.getEventType());
    }
}
