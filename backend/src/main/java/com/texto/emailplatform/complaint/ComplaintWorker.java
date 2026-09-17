package com.texto.emailplatform.complaint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.queue.EmailQueues;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Consumes trusted complaint payloads from {@code email.complaint}.
 * Acknowledges only after {@link ComplaintIngestionService} returns (transaction committed).
 */
@Component
public class ComplaintWorker {

    private static final Logger log = LoggerFactory.getLogger(ComplaintWorker.class);

    private final ComplaintIngestionService complaintIngestionService;
    private final ObjectMapper objectMapper;
    private final EmailPlatformProperties properties;

    public ComplaintWorker(
            ComplaintIngestionService complaintIngestionService,
            ObjectMapper objectMapper,
            EmailPlatformProperties properties
    ) {
        this.complaintIngestionService = complaintIngestionService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @RabbitListener(
            queues = EmailQueues.COMPLAINT_QUEUE,
            containerFactory = "emailDeliveryListenerContainerFactory"
    )
    public void onMessage(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws IOException {
        try {
            process(message.getBody());
            channel.basicAck(tag, false);
        } catch (DataAccessException | CannotCreateTransactionException exception) {
            log.warn("Complaint worker transient failure type={}", exception.getClass().getSimpleName());
            channel.basicNack(tag, false, true);
        } catch (Exception exception) {
            log.warn("Complaint worker failed type={}", exception.getClass().getSimpleName());
            channel.basicNack(tag, false, false);
        }
    }

    public ComplaintIngestionResult process(byte[] body) {
        int max = properties.getComplaint().getMaxPayloadBytes();
        if (body != null && body.length > max) {
            return complaintIngestionService.ingestMalformed(body, "oversized");
        }
        ComplaintIngestionRequest request;
        try {
            request = objectMapper.readValue(body == null ? new byte[0] : body, ComplaintIngestionRequest.class);
        } catch (Exception exception) {
            return complaintIngestionService.ingestMalformed(body, "malformed_json");
        }
        return complaintIngestionService.ingest(request);
    }
}
