package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.bounce.BounceApplicationService;
import com.texto.emailplatform.bounce.BounceClass;
import com.texto.emailplatform.bounce.BounceClassification;
import com.texto.emailplatform.bounce.BounceEventHash;
import com.texto.emailplatform.bounce.BounceFailureKind;
import com.texto.emailplatform.bounce.BounceMetrics;
import com.texto.emailplatform.bounce.BounceRecipientMatcher;
import com.texto.emailplatform.bounce.DsnAction;
import com.texto.emailplatform.bounce.domain.BounceEventEntity;
import com.texto.emailplatform.bounce.domain.BounceEventRepository;
import com.texto.emailplatform.common.FeedbackAuthorization;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapts SES bounce events to the bounce processing pipeline.
 * Unique {@code mail.messageId} → {@code providerMessageId} correlates the outbound message.
 * State mutation uses {@link FeedbackAuthorization#SES}, not DSN token correlation.
 */
@Service
public class SesBounceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SesBounceIngestionService.class);
    static final String PROVIDER = "AWS_SES";

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
            throw new UnprocessableSesFeedbackException("invalid_ses_bounce");
        }

        String providerMessageId = event.messageId();
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new UnprocessableSesFeedbackException("missing_provider_message_id");
        }
        if (event.bouncedRecipients() == null || event.bouncedRecipients().isEmpty()) {
            throw new UnprocessableSesFeedbackException("missing_bounced_recipients");
        }

        var messages = emailMessageRepository.findByProviderMessageId(providerMessageId);
        if (messages.isEmpty()) {
            throw new UnresolvedSesFeedbackException("unmatched_provider_message_id");
        }
        if (messages.size() > 1) {
            throw new UnresolvedSesFeedbackException("ambiguous_provider_message_id");
        }

        EmailMessageEntity message = messages.getFirst();
        UUID tenantId = message.getTenantId();

        for (SesFeedbackEvent.BouncedRecipient bouncedRecipient : event.bouncedRecipients()) {
            ingestRecipient(event, message, tenantId, bouncedRecipient);
        }
    }

    private void ingestRecipient(
            SesFeedbackEvent event,
            EmailMessageEntity message,
            UUID tenantId,
            SesFeedbackEvent.BouncedRecipient bouncedRecipient
    ) {
        if (bouncedRecipient == null || bouncedRecipient.emailAddress() == null || bouncedRecipient.emailAddress().isBlank()) {
            log.warn("SES bounce: skipping blank recipient emailMessageId={}", message.getId());
            return;
        }

        String storedRecipient = BounceRecipientMatcher.matchingStoredRecipient(message, bouncedRecipient.emailAddress());
        String recipientType = BounceRecipientMatcher.recipientType(message, storedRecipient);
        BounceClassification classification = mapBounceClassification(event, bouncedRecipient);
        String eventHash = BounceEventHash.canonical(
                tenantId,
                event.messageId(),
                storedRecipient != null ? storedRecipient : bouncedRecipient.emailAddress(),
                classification.action() != null ? classification.action().name() : null,
                classification.statusCode(),
                classification.diagnosticCode(),
                event.reportingMta(),
                event.timestamp() != null ? event.timestamp().toString() : null
        );

        if (isDuplicate(eventHash, storedRecipient != null ? storedRecipient : bouncedRecipient.emailAddress(), event.feedbackId())) {
            bounceMetrics.incrementDuplicate();
            log.debug("SES bounce: duplicate ignored emailMessageId={} recipientType={}", message.getId(), recipientType);
            return;
        }

        String correlationStatus = storedRecipient != null
                ? BounceEventEntity.CORRELATION_MATCHED
                : BounceEventEntity.CORRELATION_UNMATCHED;
        BounceEventEntity bounceEvent = BounceEventEntity.create(
                tenantId,
                message.getId(),
                eventHash,
                correlationStatus,
                classification.bounceClass(),
                classification.failureKind(),
                classification.action(),
                classification.statusCode(),
                classification.diagnosticCode(),
                firstNonBlank(bouncedRecipient.diagnosticCode(), event.diagnosticCode()),
                bouncedRecipient.emailAddress(),
                storedRecipient != null ? storedRecipient : bouncedRecipient.emailAddress(),
                event.source(),
                null,
                event.reportingMta(),
                event.remoteMta(),
                event.timestamp(),
                event.timestamp(),
                null,
                PROVIDER,
                event.feedbackId()
        );
        bounceEvent.attachTrafficCorrelation(message.getRequestId(), message.getTraceId());

        try {
            bounceEventRepository.save(bounceEvent);
        } catch (DataIntegrityViolationException duplicate) {
            bounceMetrics.incrementDuplicate();
            log.debug("SES bounce: concurrent duplicate emailMessageId={}", message.getId());
            return;
        }
        bounceMetrics.incrementEvent();

        if (storedRecipient == null) {
            log.info(
                    "SES bounce: recipient not on message emailMessageId={} (no suppression)",
                    message.getId()
            );
            return;
        }

        bounceApplicationService.apply(message, storedRecipient, classification, FeedbackAuthorization.SES);
        log.info(
                "SES bounce ingested eventId={} tenantId={} emailMessageId={} recipientType={} bounceClass={} failureKind={} authorization=SES",
                bounceEvent.getId(),
                tenantId,
                message.getId(),
                recipientType,
                classification.bounceClass(),
                classification.failureKind()
        );
    }

    private boolean isDuplicate(String eventHash, String finalRecipient, String providerEventId) {
        Optional<BounceEventEntity> byHash = bounceEventRepository.findByEventHash(eventHash);
        if (byHash.isPresent()) {
            return true;
        }
        if (providerEventId == null || providerEventId.isBlank() || finalRecipient == null) {
            return false;
        }
        return bounceEventRepository
                .findByProviderAndProviderEventIdAndFinalRecipient(PROVIDER, providerEventId, finalRecipient)
                .isPresent();
    }

    private BounceClassification mapBounceClassification(
            SesFeedbackEvent event,
            SesFeedbackEvent.BouncedRecipient recipient
    ) {
        BounceClass bounceClass = mapBounceClass(event.bounceType());
        BounceFailureKind failureKind = mapBounceFailureKind(event.bounceSubType());
        String action = firstNonBlank(recipient.action(), event.action());
        String statusCode = firstNonBlank(recipient.status(), event.status());
        String diagnosticCode = firstNonBlank(recipient.diagnosticCode(), event.diagnosticCode());
        return new BounceClassification(
                bounceClass,
                failureKind,
                DsnAction.fromField(action),
                statusCode,
                diagnosticCode
        );
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

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
