package com.texto.emailplatform.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void signsAndVerifiesRoundTrip() {
        String secret = "whsec_testsecret";
        long timestamp = 1_700_000_000L;
        String body = "{\"id\":\"evt_1\",\"type\":\"email.delivered\"}";

        String signature = signer.sign(secret, timestamp, body);
        assertThat(signature).hasSize(64);
        assertThat(signer.verify(secret, timestamp, body, signature)).isTrue();
        assertThat(signer.signatureHeader(timestamp, signature))
                .isEqualTo("t=" + timestamp + ",v1=" + signature);
        assertThat(signer.verify(secret, timestamp + 1, body, signature)).isFalse();
    }
}
