package com.texto.emailplatform.delivery;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.delivery.mta.MtaClient;
import com.texto.emailplatform.delivery.mta.MtaOutcome;
import com.texto.emailplatform.delivery.mta.MtaResult;
import com.texto.emailplatform.delivery.mta.MtaSubmitRequest;
import com.texto.emailplatform.delivery.mta.SmtpEnvelope;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Composes MIME, DKIM-signs, then submits via {@link MtaClient}.
 * Does not speak SMTP itself and does not depend on Mailpit types.
 */
@Component
@Primary
public class SmtpDeliveryEngine implements DeliveryEngine {

    private static final Logger log = LoggerFactory.getLogger(SmtpDeliveryEngine.class);

    private final MtaClient mtaClient;
    private final DkimSigningService dkimSigningService;
    private final EmailPlatformProperties properties;

    public SmtpDeliveryEngine(
            MtaClient mtaClient,
            DkimSigningService dkimSigningService,
            EmailPlatformProperties properties
    ) {
        this.mtaClient = mtaClient;
        this.dkimSigningService = dkimSigningService;
        this.properties = properties;
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        if (request.from() == null || request.from().isBlank()) {
            return DeliveryResult.permanent("invalid_from", "From address is required", "550");
        }
        OutboundMimeComposer.ComposedMessage composed = OutboundMimeComposer.compose(request, Instant.now());
        if (composed.envelopeRecipients().isEmpty()) {
            return DeliveryResult.permanent("no_recipients", "No deliverable recipients", "550");
        }

        byte[] rfc822;
        try {
            rfc822 = dkimSigningService.sign(composed, Instant.now()).rfc822();
        } catch (RuntimeException exception) {
            log.warn("DKIM signing failed for messageId={}", request.messageId());
            return DeliveryResult.permanent("dkim_sign_failed", "DKIM signing failed", "550");
        }
        if (properties.getMta().isRequireDkim() && !DkimSigner.hasSignature(rfc822)) {
            return DeliveryResult.permanent("dkim_unsigned", "Refusing to submit unsigned mail", "550");
        }

        MtaResult result = mtaClient.submit(new MtaSubmitRequest(
                new SmtpEnvelope(composed.mailFrom(), composed.envelopeRecipients()),
                rfc822,
                composed.messageId()
        ));
        return toDeliveryResult(result, composed.messageId());
    }

    static DeliveryResult toDeliveryResult(MtaResult result, String messageId) {
        return switch (result.outcome()) {
            case SUCCESS -> DeliveryResult.success(
                    result.providerMessageId() == null ? messageId : result.providerMessageId(),
                    result.diagnostic() == null ? "250 Ok" : result.diagnostic()
            );
            case PERMANENT_FAILURE, TLS_FAILURE -> DeliveryResult.permanent(
                    category(result.outcome()),
                    result.diagnostic() == null ? result.outcome().name() : result.diagnostic(),
                    result.smtpCode()
            );
            case TEMPORARY_FAILURE, CONNECTION_FAILURE, TIMEOUT, UNKNOWN_FAILURE -> DeliveryResult.temporary(
                    category(result.outcome()),
                    result.diagnostic() == null ? result.outcome().name() : result.diagnostic(),
                    result.smtpCode()
            );
        };
    }

    private static String category(MtaOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> "ok";
            case TEMPORARY_FAILURE -> "smtp_4xx";
            case PERMANENT_FAILURE -> "smtp_5xx";
            case CONNECTION_FAILURE -> "connection_failure";
            case TLS_FAILURE -> "tls_failure";
            case TIMEOUT -> "timeout";
            case UNKNOWN_FAILURE -> "io_error";
        };
    }
}
