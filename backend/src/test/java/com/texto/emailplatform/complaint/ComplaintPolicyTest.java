package com.texto.emailplatform.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComplaintPolicyTest {

    @Test
    void tokenAndRecipientSuppresses() {
        assertThat(ComplaintPolicy.decide(true, true).suppresses()).isTrue();
    }

    @Test
    void missingTokenIsNoAction() {
        assertThat(ComplaintPolicy.decide(false, true)).isEqualTo(ComplaintPolicyDecision.NO_ACTION);
    }

    @Test
    void missingRecipientIsNoAction() {
        assertThat(ComplaintPolicy.decide(true, false)).isEqualTo(ComplaintPolicyDecision.NO_ACTION);
    }
}
