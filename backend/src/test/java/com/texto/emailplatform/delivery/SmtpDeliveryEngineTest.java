package com.texto.emailplatform.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.delivery.mta.MtaClient;
import com.texto.emailplatform.delivery.mta.MtaOutcome;
import com.texto.emailplatform.delivery.mta.MtaResult;
import com.texto.emailplatform.delivery.mta.MtaSubmitRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmtpDeliveryEngineTest {

    @Mock
    private MtaClient mtaClient;

    private SmtpDeliveryEngine engine;
    private DkimSigningService dkimSigningService;

    @BeforeEach
    void setUp() {
        dkimSigningService = new DkimSigningService();
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setRequireDkim(true);
        engine = new SmtpDeliveryEngine(mtaClient, dkimSigningService, properties);
    }

    @Test
    void signsThenSubmitsExactRfc822ThroughMtaClient() {
        when(mtaClient.submit(any())).thenReturn(MtaResult.success("250", "250 Ok", "<id>"));

        DeliveryEngine.DeliveryResult result = engine.deliver(new DeliveryEngine.DeliveryRequest(
                "noreply@acme.texto.test",
                List.of("person@example.com"),
                List.of(),
                List.of(),
                null,
                "Hello",
                "body",
                null
        ));

        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.SUCCESS);
        ArgumentCaptor<MtaSubmitRequest> captor = ArgumentCaptor.forClass(MtaSubmitRequest.class);
        verify(mtaClient).submit(captor.capture());
        MtaSubmitRequest submitted = captor.getValue();
        String rfc822 = new String(submitted.rfc822(), StandardCharsets.UTF_8);
        assertThat(rfc822).startsWith("DKIM-Signature:");
        assertThat(rfc822).contains("From: noreply@acme.texto.test");
        assertThat(rfc822).contains("Subject: Hello");
        assertThat(submitted.envelope().mailFrom()).isEqualTo("noreply@acme.texto.test");
        assertThat(submitted.envelope().recipients()).containsExactly("person@example.com");
        assertThat(DkimSigner.verify(submitted.rfc822(), dkimSigningService.publicKey("acme.texto.test"))).isTrue();
    }

    @Test
    void refusesUnsignedFallback() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setRequireDkim(true);
        DkimSigningService failing = new DkimSigningService() {
            @Override
            public SignedMime sign(OutboundMimeComposer.ComposedMessage composed, java.time.Instant now) {
                return new SignedMime(composed.rfc822(), null, null);
            }
        };
        SmtpDeliveryEngine unsignedEngine = new SmtpDeliveryEngine(mtaClient, failing, properties);

        DeliveryEngine.DeliveryResult result = unsignedEngine.deliver(new DeliveryEngine.DeliveryRequest(
                "noreply@acme.texto.test",
                List.of("person@example.com"),
                List.of(),
                List.of(),
                null,
                "Hello",
                "body",
                null
        ));

        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.PERMANENT_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("dkim_unsigned");
        verify(mtaClient, never()).submit(any());
    }

    @Test
    void mapsTemporaryMtaResultWithoutRetryingInsideEngine() {
        when(mtaClient.submit(any())).thenReturn(MtaResult.failure(
                MtaOutcome.TEMPORARY_FAILURE, true, "421", null, "try later"
        ));
        DeliveryEngine.DeliveryResult result = engine.deliver(request());
        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.TEMPORARY_FAILURE);
        assertThat(result.retryable()).isTrue();
        assertThat(result.smtpCode()).isEqualTo("421");
        verify(mtaClient).submit(any());
    }

    @Test
    void engineDoesNotReferenceMailpit() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/texto/emailplatform/delivery/SmtpDeliveryEngine.java")
        );
        assertThat(source).doesNotContain("MailpitMtaClient");
        assertThat(source).doesNotContain("java.net.Socket");
        assertThat(source).contains("MtaClient");
    }

    private static DeliveryEngine.DeliveryRequest request() {
        return new DeliveryEngine.DeliveryRequest(
                "noreply@acme.texto.test",
                List.of("person@example.com"),
                List.of(),
                List.of(),
                null,
                "Hello",
                "body",
                null
        );
    }
}
