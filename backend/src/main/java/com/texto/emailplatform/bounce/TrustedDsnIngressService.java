package com.texto.emailplatform.bounce;

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
 * Infrastructure-only DSN ingress. Not a public API. Publishes to {@code email.bounce}.
 */
@Service
public class TrustedDsnIngressService {

    private static final Logger log = LoggerFactory.getLogger(TrustedDsnIngressService.class);

    private final RabbitTemplate rabbitTemplate;
    private final EmailPlatformProperties properties;
    private final BounceTokenExtractor tokenExtractor;

    public TrustedDsnIngressService(
            RabbitTemplate rabbitTemplate,
            EmailPlatformProperties properties,
            BounceTokenExtractor tokenExtractor
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.tokenExtractor = tokenExtractor;
    }

    public void accept(String envelopeRecipient, byte[] rawRfc822) {
        byte[] payload = rawRfc822 == null ? new byte[0] : rawRfc822;
        int max = properties.getBounce().getMaxRfc822Bytes();
        if (payload.length > max) {
            log.warn("Trusted DSN ingress rejected oversized payload bytes={}", payload.length);
            throw new IllegalArgumentException("oversized");
        }
        Message message = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_BYTES)
                .setHeader(EmailQueues.ENVELOPE_RECIPIENT_HEADER, envelopeRecipient == null ? "" : envelopeRecipient)
                .build();
        rabbitTemplate.send(EmailQueues.EMAIL_EXCHANGE, EmailQueues.BOUNCE_ROUTING_KEY, message);
        log.info(
                "Trusted DSN queued tokenFp={} bytes={}",
                BounceCorrelationToken.fingerprint(tokenExtractor.extract(envelopeRecipient).orElse(null)),
                payload.length
        );
    }
}
