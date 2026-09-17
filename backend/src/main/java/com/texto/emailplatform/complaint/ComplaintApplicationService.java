package com.texto.emailplatform.complaint;

import com.texto.emailplatform.bounce.BounceRecipientMatcher;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import com.texto.emailplatform.webhook.WebhookEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Applies complaint policy. Does not change delivery status. Does not call BouncePolicy.
 */
@Service
public class ComplaintApplicationService {

    public static final String REASON_COMPLAINT = "COMPLAINT";

    private static final Logger log = LoggerFactory.getLogger(ComplaintApplicationService.class);

    private final SuppressionService suppressionService;
    private final WebhookEventPublisher webhookEventPublisher;
    private final ComplaintMetrics complaintMetrics;

    public ComplaintApplicationService(
            SuppressionService suppressionService,
            WebhookEventPublisher webhookEventPublisher,
            ComplaintMetrics complaintMetrics
    ) {
        this.suppressionService = suppressionService;
        this.webhookEventPublisher = webhookEventPublisher;
        this.complaintMetrics = complaintMetrics;
    }

    public ComplaintPolicyDecision apply(
            EmailMessageEntity message,
            String storedRecipient,
            boolean tokenCorrelated
    ) {
        boolean onMessage = storedRecipient != null;
        ComplaintPolicyDecision decision = ComplaintPolicy.decide(tokenCorrelated, onMessage);
        if (!decision.suppresses() || message == null) {
            return decision;
        }
        suppressionService.recordComplaint(
                message.getTenantId(),
                storedRecipient,
                message.getId(),
                REASON_COMPLAINT
        );
        complaintMetrics.incrementSuppressed();
        publishWebhookSafely(message);
        log.info(
                "Complaint policy applied emailMessageId={} tenantId={} action=SUPPRESS (recipient not logged)",
                message.getId(),
                message.getTenantId()
        );
        return decision;
    }

    public static String matchingRecipient(EmailMessageEntity message, String rawRecipient) {
        return BounceRecipientMatcher.matchingStoredRecipient(message, rawRecipient);
    }

    private void publishWebhookSafely(EmailMessageEntity message) {
        try {
            webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_COMPLAINT, message);
        } catch (RuntimeException exception) {
            log.warn(
                    "Complaint webhook publish failed type={} emailMessageId={}",
                    exception.getClass().getSimpleName(),
                    message.getId()
            );
        }
    }
}
