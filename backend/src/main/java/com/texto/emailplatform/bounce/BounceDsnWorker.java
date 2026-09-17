package com.texto.emailplatform.bounce;

import com.rabbitmq.client.Channel;
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
 * Consumes trusted DSN payloads from {@code email.bounce}.
 * Acknowledges only after {@link DsnIngestionService} returns (transaction committed).
 */
@Component
public class BounceDsnWorker {

    private static final Logger log = LoggerFactory.getLogger(BounceDsnWorker.class);

    private final DsnIngestionService dsnIngestionService;

    public BounceDsnWorker(DsnIngestionService dsnIngestionService) {
        this.dsnIngestionService = dsnIngestionService;
    }

    @RabbitListener(
            queues = EmailQueues.BOUNCE_QUEUE,
            containerFactory = "emailDeliveryListenerContainerFactory"
    )
    public void onMessage(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws IOException {
        try {
            process(envelopeRecipient(message), message.getBody());
            channel.basicAck(tag, false);
        } catch (DataAccessException | CannotCreateTransactionException exception) {
            log.warn("Bounce DSN worker transient failure type={}", exception.getClass().getSimpleName());
            channel.basicNack(tag, false, true);
        } catch (Exception exception) {
            log.warn("Bounce DSN worker failed type={}", exception.getClass().getSimpleName());
            channel.basicNack(tag, false, false);
        }
    }

    public DsnIngestionResult process(String envelopeRecipient, byte[] rawRfc822) {
        return dsnIngestionService.ingest(new DsnIngestionRequest(rawRfc822, envelopeRecipient));
    }

    private static String envelopeRecipient(Message message) {
        Object header = message.getMessageProperties().getHeaders().get(EmailQueues.ENVELOPE_RECIPIENT_HEADER);
        return header == null ? null : header.toString();
    }
}
