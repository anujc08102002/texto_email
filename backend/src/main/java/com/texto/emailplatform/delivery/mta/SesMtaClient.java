package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.RawMessage;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

/**
 * Amazon SES API MTA client. Submits already-composed RFC 822 bytes as SESv2 RAW content.
 */
public class SesMtaClient implements MtaClient {

    private final SesV2Client sesV2Client;
    private final EmailPlatformProperties properties;
    private final PublicDeliveryGuard publicDeliveryGuard;

    public SesMtaClient(SesV2Client sesV2Client, EmailPlatformProperties properties) {
        this(sesV2Client, properties, null);
    }

    public SesMtaClient(
            SesV2Client sesV2Client,
            EmailPlatformProperties properties,
            PublicDeliveryGuard publicDeliveryGuard
    ) {
        this.sesV2Client = sesV2Client;
        this.properties = properties;
        this.publicDeliveryGuard = publicDeliveryGuard;
    }

    @Override
    public MtaResult submit(MtaSubmitRequest request) {
        if (publicDeliveryGuard != null && !publicDeliveryGuard.allowMtaSubmit()) {
            return MtaResult.of(
                    MtaOutcome.PERMANENT_FAILURE,
                    "550",
                    "550 public Internet delivery is disabled",
                    "public_delivery_disabled",
                    "Public Internet delivery is disabled"
            );
        }
        try {
            SendEmailResponse response = sesV2Client.sendEmail(sendEmailRequest(request));
            return MtaResult.success(
                    "250",
                    "250 SES accepted message " + response.messageId(),
                    response.messageId()
            );
        } catch (AwsServiceException exception) {
            return fromAwsServiceException(exception);
        } catch (SdkClientException exception) {
            return fromSdkClientException(exception);
        }
    }

    private SendEmailRequest sendEmailRequest(MtaSubmitRequest request) {
        SendEmailRequest.Builder builder = SendEmailRequest.builder()
                .content(EmailContent.builder()
                        .raw(RawMessage.builder()
                                .data(SdkBytes.fromByteArray(request.rfc822()))
                                .build())
                        .build());
        String configurationSetName = properties.getSes().getConfigurationSetName();
        if (configurationSetName != null && !configurationSetName.isBlank()) {
            builder.configurationSetName(configurationSetName.trim());
        }
        return builder.build();
    }

    private static MtaResult fromAwsServiceException(AwsServiceException exception) {
        int statusCode = exception.statusCode();
        String errorCode = errorCode(exception);
        String message = safeMessage(exception);
        if (exception.isThrottlingException() || statusCode == 429 || containsAny(errorCode, "throttl", "rate", "limit")) {
            return MtaResult.of(MtaOutcome.TEMPORARY_FAILURE, "451", message, "ses_throttling", message);
        }
        if (statusCode >= 500 || containsAny(errorCode, "serviceunavailable", "internalfailure", "tempor")) {
            return MtaResult.of(MtaOutcome.TEMPORARY_FAILURE, "451", message, "ses_service_failure", message);
        }
        if (statusCode == 403 || containsAny(errorCode, "accessdenied", "unauthorized", "forbidden")) {
            return MtaResult.of(MtaOutcome.PERMANENT_FAILURE, "550", message, "ses_access_denied", message);
        }
        if (containsAny(errorCode, "messagerejected", "mailfromdomainnotverified")) {
            return MtaResult.of(MtaOutcome.PERMANENT_FAILURE, "550", message, "ses_message_rejected", message);
        }
        if (statusCode >= 400 && statusCode < 500) {
            return MtaResult.of(MtaOutcome.PERMANENT_FAILURE, "550", message, "ses_invalid_request", message);
        }
        return MtaResult.of(MtaOutcome.TEMPORARY_FAILURE, "451", message, "ses_service_failure", message);
    }

    private static MtaResult fromSdkClientException(SdkClientException exception) {
        String message = safeMessage(exception);
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("credential") || normalized.contains("auth")) {
            return MtaResult.of(MtaOutcome.PERMANENT_FAILURE, "550", message, "ses_credentials", message);
        }
        return MtaResult.of(MtaOutcome.CONNECTION_FAILURE, "451", message, "ses_client_failure", message);
    }

    private static String errorCode(AwsServiceException exception) {
        if (exception.awsErrorDetails() == null || exception.awsErrorDetails().errorCode() == null) {
            return "";
        }
        return exception.awsErrorDetails().errorCode().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "SES submission failed"
                : exception.getMessage();
    }
}
