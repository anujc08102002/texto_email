package com.texto.emailplatform.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.webhook.domain.WebhookConfigEntity;
import com.texto.emailplatform.webhook.domain.WebhookConfigRepository;
import com.texto.emailplatform.webhook.domain.WebhookEventEntity;
import com.texto.emailplatform.webhook.domain.WebhookEventRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WebhookDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);
    private static final long[] BACKOFF_SECONDS = {60, 300, 900, 3600, 21600};

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookSigner webhookSigner;
    private final WebhookSecretProtector webhookSecretProtector;
    private final EmailPlatformProperties properties;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final HttpClient httpClient;

    public WebhookDeliveryWorker(
            WebhookEventRepository webhookEventRepository,
            WebhookConfigRepository webhookConfigRepository,
            WebhookSigner webhookSigner,
            WebhookSecretProtector webhookSecretProtector,
            EmailPlatformProperties properties,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.webhookConfigRepository = webhookConfigRepository;
        this.webhookSigner = webhookSigner;
        this.webhookSecretProtector = webhookSecretProtector;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getWebhooks().getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @RabbitListener(queues = WebhookQueues.DELIVERY_QUEUE)
    @Transactional
    public void onMessage(String eventId, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            deliver(UUID.fromString(eventId));
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            log.warn("Webhook delivery failed for event {}", eventId);
            channel.basicNack(tag, false, false);
        }
    }

    @Transactional
    public void deliver(UUID eventId) {
        WebhookEventEntity event = webhookEventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }
        if (WebhookEventEntity.STATUS_DELIVERED.equals(event.getStatus())
                || WebhookEventEntity.STATUS_FAILED.equals(event.getStatus())) {
            return;
        }
        if (event.getNextAttemptAt() != null && Instant.now().isBefore(event.getNextAttemptAt())) {
            rabbitTemplate.convertAndSend(
                    WebhookQueues.WEBHOOK_EXCHANGE,
                    WebhookQueues.DELIVERY_ROUTING_KEY,
                    event.getId().toString()
            );
            return;
        }

        WebhookConfigEntity config = webhookConfigRepository.findById(event.getWebhookConfigId()).orElse(null);
        if (config == null
                || WebhookConfigEntity.STATUS_PAUSED.equals(config.getStatus())
                || WebhookConfigEntity.STATUS_DISABLED.equals(config.getStatus())) {
            event.markFailed(null, "Webhook configuration is not active");
            webhookEventRepository.save(event);
            return;
        }

        event.markDelivering();
        webhookEventRepository.save(event);

        try {
            String body = objectMapper.writeValueAsString(event.getPayload());
            String secret = webhookSecretProtector.reveal(config.getSecretHash());
            long timestamp = Instant.now().getEpochSecond();
            String signature = webhookSigner.sign(secret, timestamp, body);

            HttpRequest request = HttpRequest.newBuilder(URI.create(config.getUrl()))
                    .timeout(Duration.ofMillis(properties.getWebhooks().getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Webhook-Id", event.getId().toString())
                    .header("Webhook-Timestamp", String.valueOf(timestamp))
                    .header("Webhook-Signature", webhookSigner.signatureHeader(timestamp, signature))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                event.markDelivered(code);
                webhookEventRepository.save(event);
                return;
            }
            handleFailure(event, code, "HTTP " + code);
        } catch (Exception exception) {
            handleFailure(event, null, exception.getMessage() == null ? "network error" : exception.getMessage());
        }
    }

    private void handleFailure(WebhookEventEntity event, Integer responseCode, String error) {
        boolean retryable = responseCode == null
                || responseCode == 408
                || responseCode == 429
                || responseCode >= 500;
        int maxAttempts = properties.getWebhooks().getMaxAttempts();
        if (retryable && event.getAttemptCount() < maxAttempts) {
            Instant next = Instant.now().plusSeconds(backoffSeconds(event.getAttemptCount()) + jitterSeconds());
            event.markRetrying(responseCode == null ? 0 : responseCode, error, next);
            webhookEventRepository.save(event);
            rabbitTemplate.convertAndSend(
                    WebhookQueues.WEBHOOK_EXCHANGE,
                    WebhookQueues.DELIVERY_ROUTING_KEY,
                    event.getId().toString()
            );
            return;
        }
        event.markFailed(responseCode, error);
        webhookEventRepository.save(event);
        rabbitTemplate.convertAndSend(
                WebhookQueues.WEBHOOK_EXCHANGE,
                WebhookQueues.DLQ_ROUTING_KEY,
                event.getId().toString()
        );
    }

    private static long backoffSeconds(int attemptCount) {
        int index = Math.max(0, Math.min(attemptCount - 1, BACKOFF_SECONDS.length - 1));
        return BACKOFF_SECONDS[index];
    }

    private static long jitterSeconds() {
        return ThreadLocalRandom.current().nextLong(0, 30);
    }
}
