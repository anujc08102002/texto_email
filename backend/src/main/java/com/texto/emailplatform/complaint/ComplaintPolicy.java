package com.texto.emailplatform.complaint;

import com.texto.emailplatform.common.FeedbackAuthorization;

/**
 * Maps a correlated complaint onto a suppression decision.
 * Does not parse payloads, correlate tenants, or mutate persistence.
 *
 * <p>DSN/token complaints require {@link FeedbackAuthorization#TOKEN}.
 * SES SQS complaints may suppress only with {@link FeedbackAuthorization#SES}.
 */
public final class ComplaintPolicy {

    private ComplaintPolicy() {
    }

    public static ComplaintPolicyDecision decide(boolean tokenCorrelated, boolean recipientOnMessage) {
        return decide(
                tokenCorrelated ? FeedbackAuthorization.TOKEN : FeedbackAuthorization.NONE,
                recipientOnMessage
        );
    }

    public static ComplaintPolicyDecision decide(
            FeedbackAuthorization authorization,
            boolean recipientOnMessage
    ) {
        if (authorization == null || !authorization.allowsStateMutation() || !recipientOnMessage) {
            return ComplaintPolicyDecision.NO_ACTION;
        }
        return ComplaintPolicyDecision.SUPPRESS;
    }
}
