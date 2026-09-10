package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.MessagingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class SmtpExceptionClassifierTest {

    @Test
    void maps4xxAsTemporary() {
        MtaResult result = SmtpExceptionClassifier.classify(new MessagingException("450 4.7.1 try later"));
        assertThat(result.outcome()).isEqualTo(MtaOutcome.TEMPORARY_FAILURE);
        assertThat(result.retryable()).isTrue();
        assertThat(result.smtpCode()).isEqualTo("450");
        assertThat(result.enhancedStatusCode()).isEqualTo("4.7.1");
    }

    @Test
    void maps5xxAsPermanent() {
        MtaResult result = SmtpExceptionClassifier.classify(new MessagingException("550 5.1.1 mailbox unavailable"));
        assertThat(result.outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
        assertThat(result.retryable()).isFalse();
        assertThat(result.smtpCode()).isEqualTo("550");
    }

    @Test
    void mapsTimeout() {
        MtaResult result = SmtpExceptionClassifier.classify(new SocketTimeoutException("Read timed out"));
        assertThat(result.outcome()).isEqualTo(MtaOutcome.TIMEOUT);
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void mapsConnectionFailure() {
        MtaResult result = SmtpExceptionClassifier.classify(new ConnectException("Connection refused"));
        assertThat(result.outcome()).isEqualTo(MtaOutcome.CONNECTION_FAILURE);
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void mapsTlsFailure() {
        MtaResult result = SmtpExceptionClassifier.classify(new SSLHandshakeException("PKIX path building failed"));
        assertThat(result.outcome()).isEqualTo(MtaOutcome.TLS_FAILURE);
        assertThat(result.retryable()).isFalse();
    }
}
