package com.texto.emailplatform.email;

import com.texto.emailplatform.bounce.BounceCorrelationToken;
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
    private final com.texto.emailplatform.attachment.AttachmentService attachments;

    public EmailDeliveryProcessor(
            EmailMessageRepository emailMessageRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryEngine deliveryEngine,
            EmailQueuePublisher emailQueuePublisher,
            SuppressionService suppressionService,
            WebhookEventPublisher webhookEventPublisher
    ) {
        this(emailMessageRepository, deliveryAttemptRepository, deliveryEngine, emailQueuePublisher, suppressionService, webhookEventPublisher, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EmailDeliveryProcessor(
            EmailMessageRepository emailMessageRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryEngine deliveryEngine,
            EmailQueuePublisher emailQueuePublisher,
            SuppressionService suppressionService,
            WebhookEventPublisher webhookEventPublisher,
            com.texto.emailplatform.attachment.AttachmentService attachments
    ) {
        this.emailMessageRepository = emailMessageRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryEngine = deliveryEngine;
        this.emailQueuePublisher = emailQueuePublisher;
        this.suppressionService = suppressionService;
        this.webhookEventPublisher = webhookEventPublisher;
        this.attachments = attachments;
    }

    @Transactional
    public void process(UUID messageId, UUID tenantId, int attempt) {
        EmailMessageEntity message = emailMessageRepository.lockByIdAndTenantId(messageId, tenantId).orElse(null);
        if (message == null) {
            log.warn("Email message {} not found for tenant {}", messageId, tenantId);
            return;
        }
        if (MessageStateMachine.isTerminal(message.getStatus())) {
            return;
        }

        if (message.getMetadata().containsKey("campaignId")
                && java.util.stream.Stream.of(message.getRecipientsTo(), message.getRecipientsCc(), message.getRecipientsBcc())
                    .flatMap(java.util.Collection::stream).anyMatch(email -> suppressionService.isSuppressed(tenantId, email))) {
            message.markSuppressed("unsubscribed_or_suppressed_before_delivery");
            emailMessageRepository.save(message);
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
            attemptEntity = deliveryAttemptRepository.save(DeliveryAttemptEntity.start(
                    messageId,
                    tenantId,
                    attempt,
                    message.getRequestId(),
                    message.getTraceId()
            ));
        }

        if (!DeliveryAttemptEntity.STARTED.equals(attemptEntity.getStatus())) {
            return;
        }

        if (message.getBounceCorrelationToken() == null) {
            message.assignBounceCorrelationToken(BounceCorrelationToken.generate());
            emailMessageRepository.save(message);
        }

        DeliveryEngine.DeliveryResult result;
        try {
        result = deliveryEngine.deliver(new DeliveryEngine.DeliveryRequest(
                tenantId,
                message.getFromAddress(),
                message.getRecipientsTo(),
                message.getRecipientsCc(),
                message.getRecipientsBcc(),
                message.getReplyTo(),
                message.getSubject(),
                message.getTextBody(),
                message.getHtmlBody(),
                message.getBounceCorrelationToken(),
                message.getMetadata().get("campaignUnsubscribeUrl") instanceof String url ? url : null,
                message.getMetadata().containsKey("attachmentIds") ? attachments.load(tenantId,message.getMetadata().get("attachmentIds")) : java.util.List.of()
        ));
        } catch(com.texto.emailplatform.common.exception.ApiException exception) {
            if(!exception.getCode().startsWith("ATTACHMENT")) throw exception;
            result=exception.getStatus()==503
                    ? DeliveryEngine.DeliveryResult.temporary("attachment_unavailable",exception.getMessage(),null)
                    : DeliveryEngine.DeliveryResult.permanent("attachment_unavailable",exception.getMessage(),null);
        }
        message.recordRfc822MessageId(result.rfc822MessageId());

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
        log.info(
                "MTA accepted messageId={} tenantId={} queueId={} smtpCode={} (handoff only; recipient delivery not confirmed)",
                message.getId(),
                message.getTenantId(),
                result.providerMessageId(),
                result.smtpCode()
        );
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

        // SES credentials, identity/configuration rejection, DKIM failures and the public
        // delivery kill switch also use synthetic 550 codes. They are not recipient bounces.
        // SES recipient bounces arrive through the verified feedback ingestion path.
        boolean bounce = "550".equals(result.smtpCode()) && "smtp_550".equals(result.errorCategory());
        if (bounce) {
            message.markBounced(
                    result.errorCategory() == null ? "permanent_bounce" : result.errorCategory(),
                    result.errorMessage()
            );
            for (String recipient : message.getRecipientsTo()) {
                message.recordBouncedRecipient(recipient);
                suppressionService.recordBounce(message.getTenantId(), recipient, message.getId(), "smtp_550");
            }
            emailMessageRepository.save(message);
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
