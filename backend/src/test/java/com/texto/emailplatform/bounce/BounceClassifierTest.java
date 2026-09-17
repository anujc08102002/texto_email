package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BounceClassifierTest {

    @Test
    void failedFiveIsHardAddressRelated() {
        BounceClassification result = BounceClassifier.classify("failed", "5.1.1", "smtp; 550 5.1.1 User unknown");
        assertThat(result.bounceClass()).isEqualTo(BounceClass.HARD_BOUNCE);
        assertThat(result.failureKind()).isEqualTo(BounceFailureKind.ADDRESS_RELATED);
        assertThat(result.statusCode()).isEqualTo("5.1.1");
    }

    @Test
    void delayedIsSoftEvenIfStatusLooksPermanent() {
        BounceClassification result = BounceClassifier.classify("delayed", "5.0.0", null);
        assertThat(result.bounceClass()).isEqualTo(BounceClass.SOFT_BOUNCE);
        assertThat(result.action()).isEqualTo(DsnAction.DELAYED);
    }

    @Test
    void failedFourIsSoftTemporary() {
        BounceClassification result = BounceClassifier.classify("failed", "4.4.1", "smtp; 421 try later");
        assertThat(result.bounceClass()).isEqualTo(BounceClass.SOFT_BOUNCE);
        assertThat(result.failureKind()).isEqualTo(BounceFailureKind.TEMPORARY_RECIPIENT);
    }

    @Test
    void mailboxSubjectIsMailboxUnavailable() {
        BounceClassification result = BounceClassifier.classify("failed", "5.2.1", null);
        assertThat(result.failureKind()).isEqualTo(BounceFailureKind.MAILBOX_UNAVAILABLE);
        assertThat(result.bounceClass()).isEqualTo(BounceClass.HARD_BOUNCE);
    }

    @Test
    void policySubjectIsPolicyRejection() {
        BounceClassification result = BounceClassifier.classify("FAILED", "5.7.1", "smtp; 550 5.7.1");
        assertThat(result.failureKind()).isEqualTo(BounceFailureKind.POLICY_REJECTION);
        assertThat(result.bounceClass()).isEqualTo(BounceClass.HARD_BOUNCE);
    }

    @Test
    void malformedStatusFallsBackToDiagnosticNotFirstDigitAlone() {
        BounceClassification result = BounceClassifier.classify("failed", "bogus", "smtp; 450 mailbox busy");
        assertThat(result.bounceClass()).isEqualTo(BounceClass.SOFT_BOUNCE);
        assertThat(result.statusCode()).isEqualTo("bogus");
        assertThat(result.failureKind()).isEqualTo(BounceFailureKind.TEMPORARY_RECIPIENT);
    }

    @Test
    void missingStatusAndDiagnosticIsUnknown() {
        BounceClassification result = BounceClassifier.classify("failed", null, null);
        assertThat(result.bounceClass()).isEqualTo(BounceClass.UNKNOWN);
        assertThat(result.failureKind()).isEqualTo(BounceFailureKind.UNKNOWN);
    }
}
