package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PostfixMtaClientTest {

    @Test
    void killSwitchPreventsSmtpSubmission() {
        AtomicBoolean submitted = new AtomicBoolean(false);
        SmtpSubmitter submitter = new SmtpSubmitter() {
            @Override
            public MtaResult submit(SmtpEndpoint endpoint, MtaSubmitRequest request) {
                submitted.set(true);
                return MtaResult.success("250", "250 Ok", "SHOULD_NOT");
            }
        };
        PublicDeliveryGuard guard = mock(PublicDeliveryGuard.class);
        when(guard.allowMtaSubmit()).thenReturn(false);

        PostfixMtaClient client = new PostfixMtaClient(new EmailPlatformProperties(), submitter, guard);
        MtaResult result = client.submit(new MtaSubmitRequest(
                new SmtpEnvelope("noreply@acme.example", List.of("alice@gmail.com")),
                "From: a\r\n\r\nbody".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(result.outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
        assertThat(result.errorCategory()).isEqualTo("public_delivery_disabled");
        assertThat(submitted.get()).isFalse();
    }
}
