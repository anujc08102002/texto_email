package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class SmtpExceptionClassifierTest {

    @Test
    void mapsSmtpReplyClasses() {
        assertThat(SmtpExceptionClassifier.fromReply(250, "250 2.0.0 Ok: queued as ABC123").outcome())
                .isEqualTo(MtaOutcome.SUCCESS);
        assertThat(SmtpExceptionClassifier.fromReply(250, "250 2.0.0 Ok: queued as ABC123").providerMessageId())
                .isEqualTo("ABC123");
        assertThat(SmtpExceptionClassifier.fromReply(451, "451 try later").outcome())
                .isEqualTo(MtaOutcome.TEMPORARY_FAILURE);
        assertThat(SmtpExceptionClassifier.fromReply(550, "550 mailbox unavailable").outcome())
                .isEqualTo(MtaOutcome.PERMANENT_FAILURE);
        assertThat(SmtpExceptionClassifier.fromReply(550, "550 mailbox unavailable").errorCategory())
                .isEqualTo("smtp_550");
    }

    @Test
    void mapsTransportFailures() {
        assertThat(SmtpExceptionClassifier.classify(new SocketTimeoutException("read timed out")).outcome())
                .isEqualTo(MtaOutcome.TIMEOUT);
        assertThat(SmtpExceptionClassifier.classify(new ConnectException("refused")).outcome())
                .isEqualTo(MtaOutcome.CONNECTION_FAILURE);
        assertThat(SmtpExceptionClassifier.classify(new SSLHandshakeException("handshake")).outcome())
                .isEqualTo(MtaOutcome.TLS_FAILURE);
        assertThat(SmtpExceptionClassifier.classify(new SmtpResponseException(421, "421 busy")).outcome())
                .isEqualTo(MtaOutcome.TEMPORARY_FAILURE);
        assertThat(SmtpExceptionClassifier.classify(new SmtpResponseException(554, "554 5.7.1 Relay access denied")).outcome())
                .isEqualTo(MtaOutcome.PERMANENT_FAILURE);
    }
}
