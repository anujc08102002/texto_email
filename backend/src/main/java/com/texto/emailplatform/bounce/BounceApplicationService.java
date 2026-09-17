package com.texto.emailplatform.bounce;

import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import com.texto.emailplatform.webhook.WebhookEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Applies a bounce policy decision to a correlated outbound message.
 * Recipient-specific: never mutates unrelated recipients or other tenants.
 */
@Service
public class BounceApplicationService {

    public static final String REASON_HARD_BOUNCE = "HARD_BOUNCE";
    public static final String REASON_POLICY_REJECTION = "POLICY_REJECTION";

    private static final Logger log = LoggerFactory.getLogger(BounceApplicationService.class);

    private final EmailMessageRepository emailMessageRepository;
    private final SuppressionService suppressionService;
    private final WebhookEventPublisher webhookEventPublisher;
    private final BounceMetrics bounceMetrics;

    public BounceApplicationService(
            EmailMessageRepository emailMessageRepository,
            SuppressionService suppressionService,
            WebhookEventPublisher webhookEventPublisher,
            BounceMetrics bounceMetrics
    ) {
        this.emailMessageRepository = emailMessageRepository;
        this.suppressionService = suppressionService;
        this.webhookEventPublisher = webhookEventPublisher;
        this.bounceMetrics = bounceMetrics;
    }

    public BouncePolicyDecision apply(
            EmailMessageEntity message,
            String storedRecipient,
            BounceClassification classification,
            boolean tokenCorrelated
    ) {
        boolean onMessage = storedRecipient != null;
        BouncePolicyDecision decision = BouncePolicy.decide(classification, tokenCorrelated, onMessage);
        if (decision.action() == BouncePolicyAction.NO_ACTION || message == null) {
            return decision;
        }

        if (decision.bouncesRecipient()) {
            message.recordBouncedRecipient(storedRecipient);
            if (message.allDeliverableRecipientsBounced()) {
                String reason = decision.suppresses() ? REASON_HARD_BOUNCE : REASON_POLICY_REJECTION;
                boolean transitioned = message.markBouncedIfAllowed(
                        reason,
                        classification == null ? null : classification.statusCode()
                );
                if (transitioned) {
                    webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_BOUNCED, message);
                }
            }
        }

        if (decision.defersRecipient()) {
            message.recordSoftBouncedRecipient(storedRecipient);
        }

        if (decision.suppresses()) {
            suppressionService.recordBounce(
                    message.getTenantId(),
                    storedRecipient,
                    message.getId(),
                    REASON_HARD_BOUNCE
            );
            bounceMetrics.incrementSuppressedFromBounce();
        }

        emailMessageRepository.save(message);
        log.info(
                "DSN policy applied emailMessageId={} tenantId={} action={} bounceClass={} failureKind={} messageStatus={} (recipient not logged)",
                message.getId(),
                message.getTenantId(),
                decision.action(),
                classification == null ? null : classification.bounceClass(),
                classification == null ? null : classification.failureKind(),
                message.getStatus()
        );
        return decision;
    }
}
