package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

class SesMtaClientTest {

    @Test
    void successfulSubmissionReturnsProviderMessageId() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder()
                .messageId("ses-message-id")
                .build());

        MtaResult result = client(ses, properties()).submit(request("From: a\r\n\r\nbody"));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.SUCCESS);
        assertThat(result.smtpCode()).isEqualTo("250");
        assertThat(result.providerMessageId()).isEqualTo("ses-message-id");
    }

    @Test
    void rfc822ContentReachesSesUnchanged() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder()
                .messageId("id")
                .build());
        byte[] rfc822 = "From: sender@texto-qa.in\r\nSubject: Hi\r\n\r\nhello".getBytes(StandardCharsets.UTF_8);

        client(ses, properties()).submit(new MtaSubmitRequest(envelope(), rfc822));

        SendEmailRequest sent = captureRequest(ses);
        assertThat(sent.content().raw().data().asByteArray()).isEqualTo(rfc822);
    }

    @Test
    void configurationSetIsIncludedWhenConfigured() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder()
                .messageId("id")
                .build());
        EmailPlatformProperties properties = properties();
        properties.getSes().setConfigurationSetName("texto-config-set");

        client(ses, properties).submit(request("From: a\r\n\r\nbody"));

        assertThat(captureRequest(ses).configurationSetName()).isEqualTo("texto-config-set");
    }

    @Test
    void blankConfigurationSetIsOmitted() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder()
                .messageId("id")
                .build());
        EmailPlatformProperties properties = properties();
        properties.getSes().setConfigurationSetName(" ");

        client(ses, properties).submit(request("From: a\r\n\r\nbody"));

        assertThat(captureRequest(ses).configurationSetName()).isNull();
    }

    @Test
    void throttlingIsTemporary() {
        MtaResult result = failing(serviceException(429, "TooManyRequestsException", "rate exceeded"));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.TEMPORARY_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("ses_throttling");
        assertThat(result.smtpCode()).isEqualTo("451");
    }

    @Test
    void serviceFailureIsTemporary() {
        MtaResult result = failing(serviceException(503, "ServiceUnavailableException", "try later"));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.TEMPORARY_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("ses_service_failure");
    }

    @Test
    void invalidRequestIsPermanent() {
        MtaResult result = failing(serviceException(400, "BadRequestException", "invalid raw message"));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("ses_invalid_request");
    }

    @Test
    void accessDeniedIsPermanent() {
        MtaResult result = failing(serviceException(403, "AccessDeniedException", "not authorized"));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("ses_access_denied");
    }

    @Test
    void networkClientExceptionIsConnectionFailure() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(SdkClientException.builder()
                .message("connection reset")
                .build());

        MtaResult result = client(ses, properties()).submit(request("From: a\r\n\r\nbody"));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.CONNECTION_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("ses_client_failure");
    }

    @Test
    void credentialClientExceptionIsPermanent() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(SdkClientException.builder()
                .message("Unable to load credentials from any provider")
                .build());

        MtaResult result = client(ses, properties()).submit(request("From: a\r\n\r\nbody"));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("ses_credentials");
    }

    @Test
    void killSwitchPreventsSesSubmission() {
        SesV2Client ses = mock(SesV2Client.class);
        PublicDeliveryGuard guard = mock(PublicDeliveryGuard.class);
        when(guard.allowMtaSubmit()).thenReturn(false);

        MtaResult result = new SesMtaClient(ses, properties(), guard).submit(request("From: a\r\n\r\nbody"));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("public_delivery_disabled");
        verify(ses, org.mockito.Mockito.never()).sendEmail(any(SendEmailRequest.class));
    }

    private static MtaResult failing(AwsServiceException exception) {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(exception);
        return client(ses, properties()).submit(request("From: a\r\n\r\nbody"));
    }

    private static AwsServiceException serviceException(int statusCode, String code, String message) {
        return AwsServiceException.builder()
                .statusCode(statusCode)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode(code)
                        .errorMessage(message)
                        .build())
                .message(message)
                .build();
    }

    private static SendEmailRequest captureRequest(SesV2Client ses) {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(captor.capture());
        return captor.getValue();
    }

    private static SesMtaClient client(SesV2Client ses, EmailPlatformProperties properties) {
        return new SesMtaClient(ses, properties);
    }

    private static EmailPlatformProperties properties() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getSes().setEnabled(true);
        properties.getSes().setRegion("ap-south-1");
        return properties;
    }

    private static MtaSubmitRequest request(String rfc822) {
        return new MtaSubmitRequest(envelope(), rfc822.getBytes(StandardCharsets.UTF_8));
    }

    private static SmtpEnvelope envelope() {
        return new SmtpEnvelope("sender@texto-qa.in", List.of("recipient@example.com"));
    }
}
