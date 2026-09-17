package com.texto.emailplatform.complaint;

/**
 * Maps a correlated complaint onto a suppression decision.
 * Does not parse payloads, correlate tenants, or mutate persistence.
 */
public final class ComplaintPolicy {

    private ComplaintPolicy() {
    }

    public static ComplaintPolicyDecision decide(boolean tokenCorrelated, boolean recipientOnMessage) {
        if (!tokenCorrelated || !recipientOnMessage) {
            return ComplaintPolicyDecision.NO_ACTION;
        }
        return ComplaintPolicyDecision.SUPPRESS;
    }
}
