package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SesReadinessServiceTest {
    @Test void blocksPlaceholderBeforeCallingAws() {
        var ses = mock(SesV2Client.class); var properties = configured();
        properties.getSes().setConfigurationSetName("YOUR_CONFIGURATION_SET_NAME");
        var result = new SesReadinessService(ses, properties).inspect("example.com");
        assertThat(result.status()).isEqualTo("ACTION_REQUIRED");
        assertThat(result.checks()).containsEntry("configurationSet", "ACTUAL_CONFIGURATION_SET_REQUIRED");
        verifyNoInteractions(ses);
    }
    @Test void reportsAccountIdentityAndEventChecksWithoutSending() {
        var ses = mock(SesV2Client.class); var properties = configured();
        when(ses.getAccount(any(GetAccountRequest.class))).thenReturn(GetAccountResponse.builder().sendingEnabled(true).productionAccessEnabled(true)
                .sendQuota(SendQuota.builder().max24HourSend(50000d).sentLast24Hours(100d).maxSendRate(14d).build()).build());
        when(ses.getConfigurationSet(any(GetConfigurationSetRequest.class))).thenReturn(GetConfigurationSetResponse.builder().build());
        when(ses.getConfigurationSetEventDestinations(any(GetConfigurationSetEventDestinationsRequest.class))).thenReturn(GetConfigurationSetEventDestinationsResponse.builder()
                .eventDestinations(EventDestination.builder().name("feedback").enabled(true).matchingEventTypes(EventType.BOUNCE, EventType.COMPLAINT).build()).build());
        when(ses.getEmailIdentity(any(GetEmailIdentityRequest.class))).thenReturn(GetEmailIdentityResponse.builder().verifiedForSendingStatus(true)
                .dkimAttributes(DkimAttributes.builder().status(DkimStatus.SUCCESS).signingEnabled(true).build()).build());
        var result = new SesReadinessService(ses, properties).inspect("example.com");
        assertThat(result.status()).isEqualTo("PROVIDER_CHECKS_PASSED");
        assertThat(result.maxSendRate()).isEqualTo(14d);
        assertThat(result.checks()).containsEntry("complaintEvents", "VERIFIED");
        verify(ses, never()).sendEmail(any(SendEmailRequest.class));
    }
    @Test void returnsSanitizedFailureWhenCredentialsAreUnavailable() {
        var ses = mock(SesV2Client.class);
        when(ses.getAccount(any(GetAccountRequest.class))).thenThrow(software.amazon.awssdk.core.exception.SdkClientException.create("private credential diagnostic"));
        var result = new SesReadinessService(ses, configured()).inspect("example.com");
        assertThat(result.status()).isEqualTo("ACTION_REQUIRED");
        assertThat(result.toString()).doesNotContain("private credential diagnostic");
    }
    private static EmailPlatformProperties configured() {
        var properties = new EmailPlatformProperties(); properties.getMta().setImplementation("ses");
        properties.getSes().setEnabled(true); properties.getSes().setConfigurationSetName("existing-mail");
        return properties;
    }
}
