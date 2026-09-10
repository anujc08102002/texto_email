package com.texto.emailplatform.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmailQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public EmailQueuePublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishDelivery(UUID messageId, UUID tenantId, int attempt) {
        rabbitTemplate.convertAndSend(
                EmailDeliveryQueues.DELIVERY_EXCHANGE,
                EmailDeliveryQueues.DELIVERY_ROUTING_KEY,
                toJson(job(messageId, tenantId, attempt))
        );
    }

    public void publishRetry(UUID messageId, UUID tenantId, int attempt) {
        rabbitTemplate.convertAndSend(
                EmailDeliveryQueues.RETRY_EXCHANGE,
                EmailDeliveryQueues.retryRoutingKey(attempt),
                toJson(job(messageId, tenantId, attempt))
        );
    }

    public void publishDlq(UUID messageId, UUID tenantId, int attempt) {
        rabbitTemplate.convertAndSend(
                EmailDeliveryQueues.DLX,
                EmailDeliveryQueues.DLQ_ROUTING_KEY,
                toJson(job(messageId, tenantId, attempt))
        );
    }

    private static Map<String, Object> job(UUID messageId, UUID tenantId, int attempt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", messageId.toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("attempt", attempt);
        return payload;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize email delivery job", exception);
        }
    }
}
