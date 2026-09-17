package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DsnParserTest {

    private DsnParser parser;

    @BeforeEach
    void setUp() {
        parser = new DsnParser(new EmailPlatformProperties());
    }

    @Test
    void parsesValidRfc3464HardBounce() {
        ParsedDsn dsn = parser.parse(DsnFixtures.hardBounce("<abc@texto.local>", "alice@example.com"));
        assertThat(dsn.success()).isTrue();
        assertThat(dsn.reportingMta()).isEqualTo("mail.example.com");
        assertThat(dsn.arrivalDate()).isNotNull();
        assertThat(dsn.originalMessageId()).contains("abc@texto.local");
        assertThat(dsn.recipients()).hasSize(1);
        DsnRecipient recipient = dsn.recipients().getFirst();
        assertThat(recipient.finalRecipient()).isEqualTo("alice@example.com");
        assertThat(recipient.originalRecipient()).isEqualTo("alice@example.com");
        assertThat(recipient.action()).isEqualToIgnoringCase("failed");
        assertThat(recipient.status()).isEqualTo("5.1.1");
        assertThat(recipient.remoteMta()).isEqualTo("mx.example.com");
        assertThat(recipient.diagnosticCode()).isEqualTo("smtp");
        assertThat(recipient.diagnosticMessage()).contains("550");
        assertThat(recipient.classification().bounceClass()).isEqualTo(BounceClass.HARD_BOUNCE);
        assertThat(recipient.classification().failureKind()).isEqualTo(BounceFailureKind.ADDRESS_RELATED);
    }

    @Test
    void classifiesSoftBounceFromDelayedAction() {
        ParsedDsn dsn = parser.parse(DsnFixtures.softBounce("<abc@texto.local>", "alice@example.com"));
        assertThat(dsn.success()).isTrue();
        DsnRecipient recipient = dsn.recipients().getFirst();
        assertThat(recipient.classification().bounceClass()).isEqualTo(BounceClass.SOFT_BOUNCE);
        assertThat(recipient.classification().action()).isEqualTo(DsnAction.DELAYED);
        assertThat(recipient.willRetryUntil()).isNotNull();
    }

    @Test
    void classifiesPolicyRejectionFromStatusSubject7() {
        ParsedDsn dsn = parser.parse(DsnFixtures.policyRejection("<abc@texto.local>", "alice@example.com"));
        assertThat(dsn.recipients().getFirst().classification().failureKind())
                .isEqualTo(BounceFailureKind.POLICY_REJECTION);
        assertThat(dsn.recipients().getFirst().classification().bounceClass())
                .isEqualTo(BounceClass.HARD_BOUNCE);
    }

    @Test
    void acceptsMissingOptionalFields() {
        ParsedDsn dsn = parser.parse(DsnFixtures.missingOptionalFields("carol@example.com"));
        assertThat(dsn.success()).isTrue();
        assertThat(dsn.arrivalDate()).isNull();
        assertThat(dsn.recipients()).hasSize(1);
        assertThat(dsn.recipients().getFirst().remoteMta()).isNull();
        assertThat(dsn.recipients().getFirst().diagnosticMessage()).isNull();
        assertThat(dsn.recipients().getFirst().finalRecipient()).isEqualTo("carol@example.com");
    }

    @Test
    void parsesMultipleRecipientBlocks() {
        ParsedDsn dsn = parser.parse(DsnFixtures.multipleRecipients("<multi@texto.local>"));
        assertThat(dsn.success()).isTrue();
        assertThat(dsn.recipients()).hasSize(2);
        assertThat(dsn.recipients().get(0).classification().bounceClass()).isEqualTo(BounceClass.HARD_BOUNCE);
        assertThat(dsn.recipients().get(1).classification().bounceClass()).isEqualTo(BounceClass.SOFT_BOUNCE);
    }

    @Test
    void unfoldsFoldedDiagnosticHeader() {
        ParsedDsn dsn = parser.parse(DsnFixtures.foldedDiagnostic("<fold@texto.local>", "alice@example.com"));
        assertThat(dsn.success()).isTrue();
        assertThat(dsn.recipients().getFirst().diagnosticMessage()).contains("550 5.1.1");
    }

    @Test
    void malformedMimeReturnsControlledFailure() {
        assertThatCode(() -> parser.parse("not-a-mime-message".getBytes())).doesNotThrowAnyException();
        ParsedDsn dsn = parser.parse("not-a-mime-message".getBytes());
        assertThat(dsn.success()).isFalse();
        assertThat(dsn.failureReason()).isNotBlank();
        assertThat(dsn.recipients()).isEmpty();
    }

    @Test
    void malformedStatusDoesNotThrowAndClassifiesUnknown() {
        ParsedDsn dsn = parser.parse(DsnFixtures.malformedStatus("alice@example.com"));
        assertThat(dsn.success()).isTrue();
        assertThat(dsn.recipients().getFirst().status()).isEqualTo("not-a-status");
        assertThat(dsn.recipients().getFirst().classification().bounceClass()).isEqualTo(BounceClass.UNKNOWN);
        assertThat(dsn.recipients().getFirst().classification().failureKind()).isEqualTo(BounceFailureKind.UNKNOWN);
    }

    @Test
    void oversizedInputIsRejected() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getBounce().setMaxRfc822Bytes(64);
        DsnParser tight = new DsnParser(properties);
        ParsedDsn dsn = tight.parse(DsnFixtures.hardBounce("<abc@texto.local>", "alice@example.com"));
        assertThat(dsn.success()).isFalse();
        assertThat(dsn.failureReason()).isEqualTo("oversized");
    }

    @Test
    void oversizedDiagnosticIsClipped() {
        ParsedDsn dsn = parser.parse(DsnFixtures.oversizedDiagnostic("alice@example.com"));
        assertThat(dsn.success()).isTrue();
        assertThat(dsn.recipients().getFirst().diagnosticMessage().length()).isLessThanOrEqualTo(512);
    }

    @Test
    void htmlHumanPartIsNotExecutedOrCopiedIntoDiagnostic() {
        ParsedDsn dsn = parser.parse(DsnFixtures.withHtmlHumanPart("<html@texto.local>", "alice@example.com"));
        assertThat(dsn.success()).isTrue();
        String diagnostic = dsn.recipients().getFirst().diagnosticMessage();
        assertThat(diagnostic).doesNotContain("<script>");
        assertThat(diagnostic).doesNotContain("evil.example");
        assertThat(diagnostic).contains("550");
    }

    @Test
    void unknownClassificationWhenActionIsDelivered() {
        byte[] raw = DsnFixtures.rfc3464(
                "delivered",
                "2.0.0",
                "smtp; 250 ok",
                "alice@example.com",
                "<ok@texto.local>",
                false,
                true,
                null
        );
        ParsedDsn dsn = parser.parse(raw);
        assertThat(dsn.recipients().getFirst().classification().bounceClass()).isEqualTo(BounceClass.UNKNOWN);
    }
}
