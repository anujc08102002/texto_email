package com.texto.emailplatform.common;

/**
 * How a bounce or complaint is allowed to mutate delivery/suppression state.
 *
 * <p>{@link #TOKEN} is the existing DSN/complaint opaque bounce-correlation-token path.
 * {@link #SES} is the trusted Amazon SES → SNS → SQS ingestion path after unique
 * provider-message-id and exact recipient correlation. {@link #NONE} records the event
 * but does not change message or suppression state.
 *
 * <p>Provider message ID or RFC 822 Message-ID alone is never {@link #TOKEN} or {@link #SES}.
 */
public enum FeedbackAuthorization {
    TOKEN,
    SES,
    NONE;

    public boolean allowsStateMutation() {
        return this == TOKEN || this == SES;
    }
}
