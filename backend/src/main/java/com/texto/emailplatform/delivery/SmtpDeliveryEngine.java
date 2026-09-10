package com.texto.emailplatform.delivery;

import com.texto.emailplatform.delivery.mta.MtaClient;
import com.texto.emailplatform.delivery.mta.MtaOutcome;
import com.texto.emailplatform.delivery.mta.MtaResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Delivery engine: compose RFC 822, DKIM-sign, then hand off to the configured {@link MtaClient}.
 * Tenant authorization, suppression, quotas, and retry remain outside this class.
 */
@Component
public class SmtpDeliveryEngine implements DeliveryEngine {

    private final MimeMessageComposer composer;
    private final MtaClient mtaClient;

    public SmtpDeliveryEngine(DkimSigningService dkimSigningService, MtaClient mtaClient) {
        this.composer = new MimeMessageComposer(dkimSigningService);
        this.mtaClient = mtaClient;
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        List<String> recipients = MimeMessageComposer.allRecipients(request);
        if (recipients.isEmpty()) {
            return DeliveryResult.permanent("no_recipients", "No deliverable recipients", "550");
        }
        MimeMessageComposer.Composed composed = composer.compose(request);
        MtaResult result = mtaClient.submit(composed.request());
        return toDeliveryResult(result, composed.messageId());
    }

    static DeliveryResult toDeliveryResult(MtaResult result, String fallbackMessageId) {
        String messageId = result.providerMessageId() == null || result.providerMessageId().isBlank()
                ? fallbackMessageId
                : result.providerMessageId();
        return switch (result.outcome()) {
            case SUCCESS -> DeliveryResult.success(messageId, result.response());
            case PERMANENT_FAILURE -> DeliveryResult.permanent(category(result), message(result), result.smtpCode());
            case TEMPORARY_FAILURE, CONNECTION_FAILURE, TIMEOUT, TLS_FAILURE ->
                    DeliveryResult.temporary(category(result), message(result), result.smtpCode());
        };
    }

    private static String category(MtaResult result) {
        if (result.errorCategory() != null && !result.errorCategory().isBlank()) {
            return result.errorCategory();
        }
        MtaOutcome outcome = result.outcome();
        return outcome == null ? "mta_error" : outcome.name().toLowerCase();
    }

    private static String message(MtaResult result) {
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return result.errorMessage();
        }
        return result.response() == null ? "MTA submission failed" : result.response();
    }
}
