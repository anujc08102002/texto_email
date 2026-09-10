package com.texto.emailplatform.email;

import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.email.domain.DeliveryAttemptEntity;
import com.texto.emailplatform.email.domain.DeliveryAttemptRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.queue.EmailDeliveryQueues;
import com.texto.emailplatform.queue.EmailQueuePublisher;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import com.texto.emailplatform.webhook.WebhookEventTypes;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional delivery processing. Kept as a separate bean so Rabbit listeners
 * (and tests) always go through a Spring proxy and get a real transaction boundary.
 */
@Service
public class EmailDeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryProcessor.class);

    private final EmailMessageRepository emailMessageRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryEngine deliveryEngine;
    private final EmailQueuePublisher emailQueuePublisher;
    private final SuppressionService suppressionService;
    private final WebhookEventPublisher webhookEventPublisher;

    public EmailDeliveryProcessor(
            EmailMessageRepository emailMessageRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryEngine deliveryEngine,
            EmailQueuePublisher emailQueuePublisher,
            SuppressionService suppressionService,
            WebhookEventPublisher webhookEventPublisher
    ) {
        this.emailMessageRepository = emailMessageRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryEngine = deliveryEngine;
        this.emailQueuePublisher = emailQueuePublisher;
        this.suppressionService = suppressionService;
        this.webhookEventPublisher = webhookEventPublisher;
    }

    @Transactional
    public void process(UUID messageId, UUID tenantId, int attempt) {
        EmailMessageEntity message = emailMessageRepository.findByIdAndTenantId(messageId, tenantId).orElse(null);
        if (message == null) {
            log.warn("Email message {} not found for tenant {}", messageId, tenantId);
            return;
        }
        if (MessageStateMachine.isTerminal(message.getStatus())) {
            return;
        }

        if (MessageStateMachine.QUEUED.equals(message.getStatus())
                || MessageStateMachine.DEFERRED.equals(message.getStatus())) {
            message.markProcessing();
            emailMessageRepository.save(message);
        }

        if (!MessageStateMachine.PROCESSING.equals(message.getStatus())
                && !MessageStateMachine.SENDING.equals(message.getStatus())) {
            log.warn("Skipping email {} in unexpected status {}", messageId, message.getStatus());
            return;
        }

        if (MessageStateMachine.PROCESSING.equals(message.getStatus())) {
            message.markSending();
            emailMessageRepository.save(message);
            webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_SENDING, message);
        }

        DeliveryAttemptEntity attemptEntity = deliveryAttemptRepository
                .findByMessageIdAndTenantIdOrderByAttemptNumberAsc(messageId, tenantId)
                .stream()
                .filter(existing -> existing.getAttemptNumber() == attempt)
                .findFirst()
                .orElse(null);
        if (attemptEntity == null) {
            message.incrementAttempt();
            emailMessageRepository.save(message);
            attemptEntity = deliveryAttemptRepository.save(DeliveryAttemptEntity.start(messageId, tenantId, attempt));
        }

        if (!DeliveryAttemptEntity.STARTED.equals(attemptEntity.getStatus())) {
            return;
        }

        DeliveryEngine.DeliveryResult result = deliveryEngine.deliver(new DeliveryEngine.DeliveryRequest(
                message.getFromAddress(),
                message.getRecipientsTo(),
                message.getRecipientsCc(),
                message.getRecipientsBcc(),
                message.getReplyTo(),
                message.getSubject(),
                message.getTextBody(),
                message.getHtmlBody(),
                message.getId(),
                tenantId
        ));

        switch (result.outcome()) {
            case SUCCESS -> handleSuccess(message, attemptEntity, result);
            case TEMPORARY_FAILURE -> handleTemporary(message, attemptEntity, result, attempt);
            case PERMANENT_FAILURE -> handlePermanent(message, attemptEntity, result);
        }
    }

    private void handleSuccess(
            EmailMessageEntity message,
            DeliveryAttemptEntity attemptEntity,
            DeliveryEngine.DeliveryResult result
    ) {
        attemptEntity.completeSuccess(result.providerResponse());
        deliveryAttemptRepository.save(attemptEntity);
        message.markDelivered(result.providerMessageId(), result.providerResponse());
        emailMessageRepository.save(message);
        webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_DELIVERED, message);
    }

    private void handleTemporary(
            EmailMessageEntity message,
            DeliveryAttemptEntity attemptEntity,
            DeliveryEngine.DeliveryResult result,
            int attempt
    ) {
        attemptEntity.completeTemporary(result.errorCategory(), result.errorMessage(), result.providerResponse());
        deliveryAttemptRepository.save(attemptEntity);

        if (attempt < message.getMaxAttempts()) {
            int nextAttempt = attempt + 1;
            long backoffSeconds = EmailDeliveryQueues.BACKOFF_SECONDS[
                    Math.min(attempt, EmailDeliveryQueues.BACKOFF_SECONDS.length) - 1
                    ];
            Instant nextAt = Instant.now().plusSeconds(backoffSeconds);
            message.markDeferred(
                    result.errorCategory() == null ? "temporary_failure" : result.errorCategory(),
                    result.errorMessage(),
                    nextAt
            );
            emailMessageRepository.save(message);
            emailQueuePublisher.publishRetry(message.getId(), message.getTenantId(), nextAttempt);
            webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_DEFERRED, message);
            return;
        }

        message.markFailed(
                result.errorCategory() == null ? "max_attempts_exceeded" : result.errorCategory(),
                result.errorMessage()
        );
        emailMessageRepository.save(message);
        emailQueuePublisher.publishDlq(message.getId(), message.getTenantId(), attempt);
        webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_FAILED, message);
    }

    private void handlePermanent(
            EmailMessageEntity message,
            DeliveryAttemptEntity attemptEntity,
            DeliveryEngine.DeliveryResult result
    ) {
        attemptEntity.completePermanent(result.errorCategory(), result.errorMessage(), result.providerResponse());
        deliveryAttemptRepository.save(attemptEntity);

        boolean bounce = "550".equals(result.smtpCode())
                || (result.errorMessage() != null && result.errorMessage().contains("550"))
                || (result.errorCategory() != null && result.errorCategory().contains("550"));
        if (bounce) {
            message.markBounced(
                    result.errorCategory() == null ? "permanent_bounce" : result.errorCategory(),
                    result.errorMessage()
            );
            emailMessageRepository.save(message);
            for (String recipient : message.getRecipientsTo()) {
                suppressionService.recordBounce(message.getTenantId(), recipient, message.getId(), "smtp_550");
            }
            webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_BOUNCED, message);
            return;
        }

        message.markFailed(
                result.errorCategory() == null ? "permanent_failure" : result.errorCategory(),
                result.errorMessage()
        );
        emailMessageRepository.save(message);
        webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_FAILED, message);
    }
}
