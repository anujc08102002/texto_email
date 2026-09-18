package com.texto.emailplatform.delivery;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.delivery.mta.MtaClient;
import com.texto.emailplatform.delivery.mta.MtaClients;
import com.texto.emailplatform.delivery.mta.MtaOutcome;
import com.texto.emailplatform.delivery.mta.MtaPropertiesValidator;
import com.texto.emailplatform.delivery.mta.MtaResult;
import com.texto.emailplatform.delivery.mta.PublicDeliveryGuard;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Delivery engine: compose RFC 822, optionally DKIM-sign, then hand off to the configured {@link MtaClient}.
 * Tenant authorization, suppression, quotas, and retry remain outside this class.
 *
 * <p>{@code SUCCESS} means the MTA accepted the message (RFC 5321 250 after DATA), not that the
 * recipient MX delivered it. Bounce/DSN ingestion is a later step.
 */
@Component
public class SmtpDeliveryEngine implements DeliveryEngine {

    private final MimeMessageComposer composer;
    private final MtaClient mtaClient;
    private final EmailPlatformProperties properties;
    private final PublicDeliveryGuard publicDeliveryGuard;

    public SmtpDeliveryEngine(
            DkimSigningService dkimSigningService,
            MtaClient mtaClient,
            EmailPlatformProperties properties
    ) {
        this(dkimSigningService, mtaClient, properties, null);
    }

    @Autowired
    public SmtpDeliveryEngine(
            DkimSigningService dkimSigningService,
            MtaClient mtaClient,
            EmailPlatformProperties properties,
            PublicDeliveryGuard publicDeliveryGuard
    ) {
        this.composer = new MimeMessageComposer(
                dkimSigningService,
                properties.getBounce().getDomain(),
                MtaPropertiesValidator.smtpIdentity(properties),
                applicationDkimEnabled(properties)
        );
        this.mtaClient = mtaClient;
        this.properties = properties;
        this.publicDeliveryGuard = publicDeliveryGuard;
    }

    private static boolean applicationDkimEnabled(EmailPlatformProperties properties) {
        return !MtaClients.SES.equals(MtaClients.normalize(properties.getMta().getImplementation()));
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        if (publicDeliveryGuard != null && !publicDeliveryGuard.allowMtaSubmit()) {
            return DeliveryResult.permanent(
                    "public_delivery_disabled",
                    "Public Internet delivery is disabled",
                    "550"
            );
        }
        List<String> recipients = MimeMessageComposer.allRecipients(request);
        if (recipients.isEmpty()) {
            return DeliveryResult.permanent("no_recipients", "No deliverable recipients", "550");
        }
        MimeMessageComposer.Composed composed;
        try {
            composed = composer.compose(request);
        } catch (DkimSigningException exception) {
            return DeliveryResult.permanent("dkim_signing_failed", "Unable to DKIM-sign the message", "550");
        }
        int maxBytes = properties.getEmail().getMaxRfc822Bytes();
        if (composed.request().rfc822().length > maxBytes) {
            return DeliveryResult.permanent(
                    "message_too_large",
                    "RFC 822 message exceeds " + maxBytes + " bytes",
                    "552"
            );
        }
        MtaResult result = mtaClient.submit(composed.request());
        return toDeliveryResult(result, composed.messageId()).withRfc822MessageId(composed.messageId());
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
