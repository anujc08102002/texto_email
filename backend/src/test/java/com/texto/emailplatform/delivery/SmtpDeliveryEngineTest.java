package com.texto.emailplatform.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.delivery.mta.MtaClient;
import com.texto.emailplatform.delivery.mta.MtaResult;
import com.texto.emailplatform.delivery.mta.MtaSubmitRequest;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
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
        when(signingService.sign(any(), any(), any(), any())).thenAnswer(invocation -> Optional.of(
                DkimSigner.sign(
                        "acme.texto.test",
                        "texto",
                        keyPair.getPrivate(),
                        invocation.getArgument(2),
                        invocation.getArgument(3)
                )
        ));

        AtomicReference<MtaSubmitRequest> captured = new AtomicReference<>();
        MtaClient mtaClient = request -> {
            captured.set(request);
            return MtaResult.success("250", "250 Ok: queued as TESTID", "TESTID");
        };

        SmtpDeliveryEngine engine = new SmtpDeliveryEngine(signingService, mtaClient);
        DeliveryEngine.DeliveryResult result = engine.deliver(new DeliveryEngine.DeliveryRequest(
                UUID.randomUUID(),
                "noreply@acme.texto.test",
                List.of("alice@texto.test"),
                List.of(),
                List.of(),
                null,
                "Hello",
                "hello world",
                null
        ));

        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.SUCCESS);
        MtaSubmitRequest handoff = captured.get();
        assertThat(handoff.envelope().mailFrom()).isEqualTo("noreply@acme.texto.test");
        assertThat(handoff.envelope().rcptTo()).containsExactly("alice@texto.test");
        String rfc822 = new String(handoff.rfc822(), StandardCharsets.UTF_8);
        assertThat(DkimSigner.hasDkimSignature(rfc822)).isTrue();
        assertThat(DkimSigner.verify(keyPair.getPublic(), rfc822)).isTrue();
    }
}
