package com.texto.emailplatform.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.texto.emailplatform.queue.EmailDeliveryQueues;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryWorker.class);

    private final EmailDeliveryProcessor emailDeliveryProcessor;
    private final ObjectMapper objectMapper;

    public EmailDeliveryWorker(EmailDeliveryProcessor emailDeliveryProcessor, ObjectMapper objectMapper) {
        this.emailDeliveryProcessor = emailDeliveryProcessor;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(
            queues = EmailDeliveryQueues.DELIVERY_QUEUE,
            containerFactory = "emailDeliveryListenerContainerFactory"
    )
    public void onMessage(String body, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            JsonNode node = objectMapper.readTree(body);
            UUID messageId = UUID.fromString(node.get("messageId").asText());
            UUID tenantId = UUID.fromString(node.get("tenantId").asText());
            int attempt = node.get("attempt").asInt();
            emailDeliveryProcessor.process(messageId, tenantId, attempt);
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            log.warn("Email delivery worker failed to process job");
            channel.basicNack(tag, false, false);
        }
    }

    /** Test / manual entry point — delegates to the transactional processor. */
    public void process(UUID messageId, UUID tenantId, int attempt) {
        emailDeliveryProcessor.process(messageId, tenantId, attempt);
    }
}
