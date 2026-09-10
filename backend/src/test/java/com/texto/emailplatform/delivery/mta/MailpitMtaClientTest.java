package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MailpitMtaClientTest {

    private static final String SIGNED_MESSAGE = """
            DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/simple; d=acme.texto.test; s=texto; bh=abc; h=from:to:subject; b=TESTSIG
            From: noreply@acme.texto.test
            To: person@example.com
            Subject: Hello
            Date: Thu, 10 Sep 2026 12:00:00 +0000
            Message-ID: <msg@acme.texto.test>
            MIME-Version: 1.0
            Content-Type: text/plain; charset=UTF-8

            body-line
            """.replace("\n", "\r\n");

    @Test
    void submitsImmutableRfc822AndSucceeds() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(FakeSmtpServer.Mode.SUCCESS)) {
            MailpitMtaClient client = new MailpitMtaClient(properties(server.port(), 2_000));
            byte[] rfc822 = SIGNED_MESSAGE.getBytes(StandardCharsets.UTF_8);
            MtaResult result = client.submit(new MtaSubmitRequest(
                    new SmtpEnvelope("noreply@acme.texto.test", java.util.List.of("person@example.com")),
                    rfc822,
                    "<msg@acme.texto.test>"
            ));
            assertThat(result.succeeded()).isTrue();
            assertThat(result.smtpCode()).isEqualTo("250");
            assertThat(client.implementation()).isEqualTo("mailpit");
            assertThat(server.lastData()).contains("DKIM-Signature: v=1; a=rsa-sha256");
            assertThat(server.lastData()).contains("b=TESTSIG");
            assertThat(server.lastData()).contains("body-line");
            assertThat(server.lastData()).doesNotContain("altered-from");
            assertThat(server.closedConnections()).isEqualTo(1);
        }
    }

    @Test
    void mapsPermanentRecipientFailure() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(FakeSmtpServer.Mode.PERMANENT_RCPT)) {
            MailpitMtaClient client = new MailpitMtaClient(properties(server.port(), 2_000));
            MtaResult result = client.submit(request());
            assertThat(result.outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
            assertThat(result.retryable()).isFalse();
            assertThat(result.smtpCode()).isEqualTo("550");
        }
    }

    @Test
    void mapsTemporaryDataFailure() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(FakeSmtpServer.Mode.TEMPORARY_DATA)) {
            MailpitMtaClient client = new MailpitMtaClient(properties(server.port(), 2_000));
            MtaResult result = client.submit(request());
            assertThat(result.outcome()).isEqualTo(MtaOutcome.TEMPORARY_FAILURE);
            assertThat(result.retryable()).isTrue();
            assertThat(result.smtpCode()).isEqualTo("450");
        }
    }

    @Test
    void mapsConnectionFailure() {
        MailpitMtaClient client = new MailpitMtaClient(properties(1, 500));
        MtaResult result = client.submit(request());
        assertThat(result.outcome()).isIn(MtaOutcome.CONNECTION_FAILURE, MtaOutcome.TIMEOUT, MtaOutcome.UNKNOWN_FAILURE);
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void mapsTimeoutWhenBannerHangs() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(FakeSmtpServer.Mode.HANG_BANNER)) {
            MailpitMtaClient client = new MailpitMtaClient(properties(server.port(), 400));
            MtaResult result = client.submit(request());
            assertThat(result.outcome()).isIn(MtaOutcome.TIMEOUT, MtaOutcome.CONNECTION_FAILURE, MtaOutcome.UNKNOWN_FAILURE);
            assertThat(result.retryable()).isTrue();
        }
    }

    @Test
    void mapsTlsFailureWhenStartTlsRequiredAgainstPlaintext() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(FakeSmtpServer.Mode.SUCCESS)) {
            EmailPlatformProperties properties = properties(server.port(), 2_000);
            properties.getMta().getStarttls().setEnabled(true);
            properties.getMta().getStarttls().setRequired(true);
            MailpitMtaClient client = new MailpitMtaClient(properties);
            MtaResult result = client.submit(request());
            assertThat(result.outcome()).isEqualTo(MtaOutcome.TLS_FAILURE);
            assertThat(result.retryable()).isFalse();
        }
    }

    private static MtaSubmitRequest request() {
        return new MtaSubmitRequest(
                new SmtpEnvelope("noreply@acme.texto.test", java.util.List.of("person@example.com")),
                SIGNED_MESSAGE.getBytes(StandardCharsets.UTF_8),
                "<msg@acme.texto.test>"
        );
    }

    private static EmailPlatformProperties properties(int port, int timeoutMs) {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().getSmtp().setHost("127.0.0.1");
        properties.getMta().getSmtp().setPort(port);
        properties.getMta().getSmtp().setConnectionTimeoutMs(timeoutMs);
        properties.getMta().getSmtp().setReadTimeoutMs(timeoutMs);
        properties.getMta().getSmtp().setWriteTimeoutMs(timeoutMs);
        properties.getMta().getStarttls().setEnabled(false);
        properties.getMta().getStarttls().setRequired(false);
        return properties;
    }
}
