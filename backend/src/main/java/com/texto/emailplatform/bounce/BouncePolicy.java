package com.texto.emailplatform.bounce;

import com.texto.emailplatform.common.FeedbackAuthorization;

/**
 * Maps bounce classification onto a state/suppression decision.
 * Does not parse MIME, correlate tenants, or mutate persistence.
 *
 * <p>DSN mutation requires {@link FeedbackAuthorization#TOKEN}.
 * SES SQS ingestion may mutate only with {@link FeedbackAuthorization#SES}.
 * Provider/RFC822 correlation maps to {@link FeedbackAuthorization#NONE}.
 */
public final class BouncePolicy {

    private BouncePolicy() {
    }

    public static BouncePolicyDecision decide(
            BounceClassification classification,
            boolean tokenCorrelated,
            boolean recipientOnMessage
    ) {
        return decide(
                classification,
                tokenCorrelated ? FeedbackAuthorization.TOKEN : FeedbackAuthorization.NONE,
                recipientOnMessage
        );
    }

    public static BouncePolicyDecision decide(
            BounceClassification classification,
            FeedbackAuthorization authorization,
            boolean recipientOnMessage
    ) {
        if (authorization == null
                || !authorization.allowsStateMutation()
                || !recipientOnMessage
                || classification == null) {
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
