package com.texto.emailplatform.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.delivery.mta.MtaClient;
import com.texto.emailplatform.delivery.mta.MtaResult;
import com.texto.emailplatform.delivery.mta.MtaSubmitRequest;
import com.texto.emailplatform.delivery.mta.PublicDeliveryGuard;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SmtpDeliveryEngineTest {

    @Test
    void composedHandoffContainsVerifiableDkimSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        DkimSigningService signingService = Mockito.mock(DkimSigningService.class);
        when(signingService.sign(any(), any(), any(), any())).thenAnswer(invocation ->
                DkimSigner.sign(
                        "acme.texto.test",
                        "texto",
                        keyPair.getPrivate(),
                        invocation.getArgument(2),
                        invocation.getArgument(3)
                )
        );

        AtomicReference<MtaSubmitRequest> captured = new AtomicReference<>();
        MtaClient mtaClient = request -> {
            captured.set(request);
            return MtaResult.success("250", "250 Ok: queued as TESTID", "TESTID");
        };

        String token = "0123456789abcdef0123456789abcdef";
        SmtpDeliveryEngine engine = new SmtpDeliveryEngine(signingService, mtaClient, new EmailPlatformProperties());
        DeliveryEngine.DeliveryResult result = engine.deliver(new DeliveryEngine.DeliveryRequest(
                UUID.randomUUID(),
                "noreply@acme.texto.test",
                List.of("alice@texto.test"),
                List.of(),
                List.of(),
                null,
                "Hello",
                "hello world",
                null,
                token
        ));

        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.SUCCESS);
        assertThat(result.rfc822MessageId()).startsWith("<").endsWith("@texto.local>");
        MtaSubmitRequest handoff = captured.get();
        assertThat(handoff.envelope().mailFrom())
                .isEqualTo("bounce+" + token + "@bounce.texto.test");
        assertThat(handoff.envelope().envelopeId()).isEqualTo(token);
        assertThat(handoff.envelope().rcptTo()).containsExactly("alice@texto.test");
        String rfc822 = new String(handoff.rfc822(), StandardCharsets.UTF_8);
        assertThat(rfc822).contains("From: noreply@acme.texto.test");
        assertThat(rfc822).doesNotContain("Return-Path:");
        assertThat(rfc822).doesNotContain("bounce+" + token);
        assertThat(DkimSigner.hasDkimSignature(rfc822)).isTrue();
        assertThat(DkimSigner.verify(keyPair.getPublic(), rfc822)).isTrue();
    }

    @Test
    void oversizedRfc822IsRejectedBeforeMtaHandoff() {
        AtomicReference<MtaSubmitRequest> captured = new AtomicReference<>();
        MtaClient mtaClient = request -> {
            captured.set(request);
            return MtaResult.success("250", "250 Ok: queued as SHOULD_NOT", "SHOULD_NOT");
        };
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getEmail().setMaxRfc822Bytes(64);
        DkimSigningService signingService = Mockito.mock(DkimSigningService.class);
        when(signingService.sign(any(), any(), any(), any())).thenReturn("DKIM-Signature: v=1; b=abc");

        SmtpDeliveryEngine engine = new SmtpDeliveryEngine(signingService, mtaClient, properties);
        DeliveryEngine.DeliveryResult result = engine.deliver(new DeliveryEngine.DeliveryRequest(
                UUID.randomUUID(),
                "noreply@acme.texto.test",
                List.of("alice@texto.test"),
                List.of(),
                List.of(),
                null,
                "Hello",
                "this body is definitely larger than sixty four bytes of rfc822",
                null
        ));

        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.PERMANENT_FAILURE);
        assertThat(result.smtpCode()).isEqualTo("552");
        assertThat(captured.get()).isNull();
    }

    @Test
    void signingFailureDoesNotSubmitUnsignedMail() {
        AtomicReference<MtaSubmitRequest> captured = new AtomicReference<>();
        MtaClient mtaClient = request -> {
            captured.set(request);
            return MtaResult.success("250", "250 Ok", "SHOULD_NOT");
        };
        DkimSigningService signingService = Mockito.mock(DkimSigningService.class);
        when(signingService.sign(any(), any(), any(), any())).thenThrow(new DkimSigningException("Unable to DKIM-sign the message"));

        SmtpDeliveryEngine engine = new SmtpDeliveryEngine(signingService, mtaClient, new EmailPlatformProperties());
        DeliveryEngine.DeliveryResult result = engine.deliver(new DeliveryEngine.DeliveryRequest(
                UUID.randomUUID(),
                "noreply@acme.texto.test",
                List.of("alice@texto.test"),
                List.of(),
                List.of(),
                null,
                "Hello",
                "body",
                null
        ));

        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.PERMANENT_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("dkim_signing_failed");
        assertThat(captured.get()).isNull();
    }

    @Test
    void publicDeliveryKillSwitchPreventsMtaHandoff() {
        AtomicReference<MtaSubmitRequest> captured = new AtomicReference<>();
        MtaClient mtaClient = request -> {
            captured.set(request);
            return MtaResult.success("250", "250 Ok", "SHOULD_NOT");
        };
        PublicDeliveryGuard guard = Mockito.mock(PublicDeliveryGuard.class);
        when(guard.allowMtaSubmit()).thenReturn(false);
        DkimSigningService signingService = Mockito.mock(DkimSigningService.class);

        SmtpDeliveryEngine engine = new SmtpDeliveryEngine(
                signingService,
                mtaClient,
                new EmailPlatformProperties(),
                guard
        );
        DeliveryEngine.DeliveryResult result = engine.deliver(new DeliveryEngine.DeliveryRequest(
                UUID.randomUUID(),
                "noreply@acme.texto.test",
                List.of("alice@gmail.com"),
                List.of(),
                List.of(),
                null,
                "Hello",
                "body",
                null
        ));

        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.PERMANENT_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("public_delivery_disabled");
        assertThat(captured.get()).isNull();
        Mockito.verify(signingService, Mockito.never()).sign(any(), any(), any(), any());
    }
}
