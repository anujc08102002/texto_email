package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.bounce.*;
import com.texto.emailplatform.bounce.domain.BounceEventEntity;
import com.texto.emailplatform.bounce.domain.BounceEventRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapts SES bounce events to the existing bounce processing pipeline.
 * Uses providerMessageId for correlation and respects existing bounce policy.
 */
@Service
public class SesBounceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SesBounceIngestionService.class);
    private static final String PROVIDER = "AWS_SES";

    private final EmailMessageRepository emailMessageRepository;
    private final BounceEventRepository bounceEventRepository;
    private final BounceApplicationService bounceApplicationService;
    private final BounceMetrics bounceMetrics;

    public SesBounceIngestionService(
            EmailMessageRepository emailMessageRepository,
            BounceEventRepository bounceEventRepository,
            BounceApplicationService bounceApplicationService,
            BounceMetrics bounceMetrics
    ) {
        this.emailMessageRepository = emailMessageRepository;
        this.bounceEventRepository = bounceEventRepository;
        this.bounceApplicationService = bounceApplicationService;
        this.bounceMetrics = bounceMetrics;
    }

    @Transactional
    public void ingest(SesFeedbackEvent event) {
        if (event == null || !event.isBounce()) {
            log.warn("Invalid SES bounce event");
            return;
        }

        String providerMessageId = event.messageId();
        if (providerMessageId == null || providerMessageId.isBlank()) {
            log.warn("SES bounce event missing provider message ID");
            return;
        }

        // Find email message by provider message ID
        var messages = emailMessageRepository.findByProviderMessageId(providerMessageId);
        if (messages.isEmpty()) {
            log.info("SES bounce: no message found for providerMessageId={} (no suppression)", providerMessageId);
            return;
        }

        if (messages.size() > 1) {
            log.warn("SES bounce: multiple messages found for providerMessageId={}, skipping to avoid incorrect correlation", providerMessageId);
            return;
        }

        EmailMessageEntity message = messages.getFirst();
        UUID tenantId = message.getTenantId();

        // Process the first bounced recipient (SES typically sends one per event)
        if (event.bouncedRecipients() == null || event.bouncedRecipients().isEmpty()) {
            log.warn("SES bounce event has no bounced recipients");
            return;
        }

        SesFeedbackEvent.BouncedRecipient bouncedRecipient = event.bouncedRecipients().getFirst();
        String recipient = bouncedRecipient.emailAddress();

        // Match recipient to stored recipients in the message
        String storedRecipient = findMatchingRecipient(message, recipient);
        if (storedRecipient == null) {
            log.info("SES bounce: recipient {} not found in message {} (no suppression)", recipient, message.getId());
            return;
        }

        // Map SES bounce fields to existing bounce model
        BounceClassification classification = mapBounceClassification(event, bouncedRecipient);
        String eventHash = BounceEventHash.canonical(
                tenantId,
                event.messageId(),
                storedRecipient,
                classification.dsnAction() != null ? classification.dsnAction().name() : null,
                classification.statusCode(),
                classification.diagnosticCode(),
                event.reportingMta(),
                event.timestamp() != null ? event.timestamp().toString() : null
        );

        // Check for duplicate
        Optional<BounceEventEntity> existing = bounceEventRepository.findByEventHash(eventHash);
        if (existing.isPresent()) {
            bounceMetrics.incrementDuplicate();
            log.debug("SES bounce: duplicate event ignored, eventId={}", existing.get().getId());
            return;
        }

        // Create bounce event entity
        BounceEventEntity bounceEvent = BounceEventEntity.create(
                tenantId,
                message.getId(),
                eventHash,
                BounceEventEntity.CORRELATION_MATCHED,
                classification.bounceClass(),
                classification.failureKind(),
                classification.dsnAction(),
                classification.statusCode(),
                classification.diagnosticCode(),
                event.diagnosticCode(), // diagnostic message
                recipient,
                storedRecipient,
                event.source(),
                null, // original message ID not available from SES
                PROVIDER,
                event.remoteMta(),
                event.timestamp(),
                event.timestamp(),
                null
        );

        bounceEventRepository.save(bounceEvent);
        bounceMetrics.incrementEvent();

        // Apply bounce policy (provider-message-ID correlation does not authorize state mutation)
        // The existing BounceApplicationService.apply() will determine if state mutation is allowed
        boolean tokenCorrelated = false; // SES events don't have bounce tokens
        bounceApplicationService.apply(message, storedRecipient, classification, tokenCorrelated);

        log.info(
                "SES bounce ingested eventId={} tenantId={} emailMessageId={} recipient={} bounceClass={} failureKind={} action={}",
                bounceEvent.getId(),
                tenantId,
                message.getId(),
                storedRecipient,
                classification.bounceClass(),
                classification.failureKind(),
                classification.dsnAction()
        );
    }

    private String findMatchingRecipient(EmailMessageEntity message, String recipient) {
        // Check if recipient matches the main recipient
        if (recipient != null && recipient.equalsIgnoreCase(message.getRecipient())) {
            return message.getRecipient();
        }
        // Could check additional recipients if the message had multiple recipients
        // For now, return null if no match
        return null;
    }

    private BounceClassification mapBounceClassification(SesFeedbackEvent event, SesFeedbackEvent.BouncedRecipient recipient) {
        // Map SES bounceType to BounceClass
        BounceClass bounceClass = mapBounceClass(event.bounceType());
        
        // Map SES bounceSubType to BounceFailureKind
        BounceFailureKind failureKind = mapBounceFailureKind(event.bounceSubType());
        
        // Map SES action to DsnAction
        DsnAction dsnAction = DsnAction.fromField(event.action());
        
        // Use SES status as statusCode
        String statusCode = event.status();
        
        // Use SES diagnosticCode as diagnosticCode
        String diagnosticCode = event.diagnosticCode();

        return new BounceClassification(bounceClass, failureKind, dsnAction, statusCode, diagnosticCode);
    }

    private BounceClass mapBounceClass(String sesBounceType) {
        if (sesBounceType == null) {
            return BounceClass.UNKNOWN;
        }
        return switch (sesBounceType.toLowerCase()) {
            case "permanent" -> BounceClass.HARD_BOUNCE;
            case "transient" -> BounceClass.SOFT_BOUNCE;
            case "undetermined" -> BounceClass.UNKNOWN;
            default -> BounceClass.UNKNOWN;
        };
    }

    private BounceFailureKind mapBounceFailureKind(String sesBounceSubType) {
        if (sesBounceSubType == null) {
            return BounceFailureKind.UNKNOWN;
        }
        return switch (sesBounceSubType.toLowerCase()) {
            case "general" -> BounceFailureKind.PERMANENT_RECIPIENT;
            case "noemail" -> BounceFailureKind.PERMANENT_RECIPIENT;
            case "suppressed" -> BounceFailureKind.POLICY_REJECTION;
            case "onaccountsuppressionlist" -> BounceFailureKind.POLICY_REJECTION;
            case "mailboxfull" -> BounceFailureKind.MAILBOX_UNAVAILABLE;
            case "mailboxoverflow" -> BounceFailureKind.MAILBOX_UNAVAILABLE;
            case "notfound" -> BounceFailureKind.PERMANENT_RECIPIENT;
            case "timeout" -> BounceFailureKind.TEMPORARY_RECIPIENT;
            case "other" -> BounceFailureKind.UNKNOWN;
            default -> BounceFailureKind.UNKNOWN;
        };
    }
}
