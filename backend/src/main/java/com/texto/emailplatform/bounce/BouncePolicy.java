package com.texto.emailplatform.bounce;

/**
 * Maps bounce classification onto a state/suppression decision.
 * Does not parse MIME, correlate tenants, or mutate persistence.
 */
public final class BouncePolicy {

    private BouncePolicy() {
    }

    public static BouncePolicyDecision decide(
            BounceClassification classification,
            boolean tokenCorrelated,
            boolean recipientOnMessage
    ) {
        if (!tokenCorrelated || !recipientOnMessage || classification == null) {
            return BouncePolicyDecision.NO_ACTION;
        }
        BounceClass bounceClass = classification.bounceClass();
        if (bounceClass == null || bounceClass == BounceClass.UNKNOWN) {
            return BouncePolicyDecision.NO_ACTION;
        }
        if (bounceClass == BounceClass.SOFT_BOUNCE) {
            return BouncePolicyDecision.DEFER;
        }
        if (classification.failureKind() == BounceFailureKind.POLICY_REJECTION) {
            return BouncePolicyDecision.BOUNCE;
        }
        return BouncePolicyDecision.SUPPRESS;
    }
}
