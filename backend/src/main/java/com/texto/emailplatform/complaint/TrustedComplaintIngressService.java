package com.texto.emailplatform.complaint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.bounce.BounceCorrelationToken;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.queue.EmailQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Infrastructure-only complaint ingress. Not a public API. Publishes to {@code email.complaint}.
 * Public complaint/feedback-loop ingestion is not enabled.
 */
@Service
public class TrustedComplaintIngressService {

    private static final Logger log = LoggerFactory.getLogger(TrustedComplaintIngressService.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final EmailPlatformProperties properties;

    public TrustedComplaintIngressService(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            EmailPlatformProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void accept(ComplaintIngestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request_required");
        }
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("unserializable");
        }
        int max = properties.getComplaint().getMaxPayloadBytes();
        if (payload.length > max) {
            log.warn("Trusted complaint ingress rejected oversized payload bytes={}", payload.length);
            throw new IllegalArgumentException("oversized");
        }
        Message message = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(EmailQueues.EMAIL_EXCHANGE, EmailQueues.COMPLAINT_ROUTING_KEY, message);
        log.info(
                "Trusted complaint queued provider={} tokenFp={} bytes={}",
                request.provider() == null ? "UNKNOWN" : request.provider(),
                BounceCorrelationToken.fingerprint(request.correlationToken()),
                payload.length
        );
    }
}
