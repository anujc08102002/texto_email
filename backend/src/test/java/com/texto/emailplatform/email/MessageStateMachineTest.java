package com.texto.emailplatform.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.texto.emailplatform.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class MessageStateMachineTest {

    @Test
    void allowsQueuedToProcessing() {
        MessageStateMachine.assertTransition(MessageStateMachine.QUEUED, MessageStateMachine.PROCESSING);
    }

    @Test
    void allowsSendingToDeliveredDeferredFailedBounced() {
        MessageStateMachine.assertTransition(MessageStateMachine.SENDING, MessageStateMachine.DELIVERED);
        MessageStateMachine.assertTransition(MessageStateMachine.SENDING, MessageStateMachine.DEFERRED);
        MessageStateMachine.assertTransition(MessageStateMachine.SENDING, MessageStateMachine.FAILED);
        MessageStateMachine.assertTransition(MessageStateMachine.SENDING, MessageStateMachine.BOUNCED);
    }

    @Test
    void allowsDeferredToProcessing() {
        MessageStateMachine.assertTransition(MessageStateMachine.DEFERRED, MessageStateMachine.PROCESSING);
    }

    @Test
    void rejectsIllegalTransitions() {
        assertThatThrownBy(() -> MessageStateMachine.assertTransition(
                MessageStateMachine.DELIVERED,
                MessageStateMachine.QUEUED
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));

        assertThatThrownBy(() -> MessageStateMachine.assertTransition(
                MessageStateMachine.QUEUED,
                MessageStateMachine.DELIVERED
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void identifiesTerminalStatuses() {
        assertThat(MessageStateMachine.isTerminal(MessageStateMachine.DELIVERED)).isTrue();
        assertThat(MessageStateMachine.isTerminal(MessageStateMachine.FAILED)).isTrue();
        assertThat(MessageStateMachine.isTerminal(MessageStateMachine.BOUNCED)).isTrue();
        assertThat(MessageStateMachine.isTerminal(MessageStateMachine.SUPPRESSED)).isTrue();
        assertThat(MessageStateMachine.isTerminal(MessageStateMachine.QUEUED)).isFalse();
        assertThat(MessageStateMachine.isTerminal(MessageStateMachine.DEFERRED)).isFalse();
    }
}
