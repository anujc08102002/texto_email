package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BouncePolicyTest {

    @Test
    void hardAddressRelatedSuppresses() {
        BounceClassification classification = BounceClassifier.classify("failed", "5.1.1", "smtp; 550 5.1.1");
        assertThat(BouncePolicy.decide(classification, true, true).action())
                .isEqualTo(BouncePolicyAction.SUPPRESS);
    }

    @Test
    void mailboxUnavailableSuppresses() {
        BounceClassification classification = BounceClassifier.classify("failed", "5.2.1", "smtp; 550 5.2.1");
        assertThat(BouncePolicy.decide(classification, true, true).suppresses()).isTrue();
    }

    @Test
    void softBounceDefersWithoutSuppress() {
        BounceClassification classification = BounceClassifier.classify("delayed", "4.2.1", "smtp; 450");
        BouncePolicyDecision decision = BouncePolicy.decide(classification, true, true);
        assertThat(decision.action()).isEqualTo(BouncePolicyAction.DEFER);
        assertThat(decision.suppresses()).isFalse();
    }

    @Test
    void unknownIsNoAction() {
        BounceClassification classification = BounceClassifier.classify("delivered", "2.0.0", null);
        assertThat(BouncePolicy.decide(classification, true, true)).isEqualTo(BouncePolicyDecision.NO_ACTION);
    }

    @Test
    void permanentPolicyRejectionBouncesWithoutSuppress() {
        BounceClassification classification = BounceClassifier.classify("failed", "5.7.1", "smtp; 550 5.7.1");
        BouncePolicyDecision decision = BouncePolicy.decide(classification, true, true);
        assertThat(decision.action()).isEqualTo(BouncePolicyAction.BOUNCE);
        assertThat(decision.suppresses()).isFalse();
        assertThat(decision.bouncesRecipient()).isTrue();
    }

    @Test
    void missingTokenOrRecipientIsNoAction() {
        BounceClassification classification = BounceClassifier.classify("failed", "5.1.1", "smtp; 550");
        assertThat(BouncePolicy.decide(classification, false, true)).isEqualTo(BouncePolicyDecision.NO_ACTION);
        assertThat(BouncePolicy.decide(classification, true, false)).isEqualTo(BouncePolicyDecision.NO_ACTION);
        assertThat(BouncePolicy.decide(null, true, true)).isEqualTo(BouncePolicyDecision.NO_ACTION);
    }
}
