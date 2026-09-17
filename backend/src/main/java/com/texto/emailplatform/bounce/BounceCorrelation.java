package com.texto.emailplatform.bounce;

import com.texto.emailplatform.email.domain.EmailMessageEntity;

/**
 * Result of mapping an untrusted DSN onto an outbound message.
 * State mutation is allowed only for token-authoritative matches.
 */
public record BounceCorrelation(EmailMessageEntity message, Source source) {

    public enum Source {
        TOKEN,
        MESSAGE_ID,
        PROVIDER,
        NONE
    }

    public static BounceCorrelation none() {
        return new BounceCorrelation(null, Source.NONE);
    }

    public static BounceCorrelation token(EmailMessageEntity message) {
        return new BounceCorrelation(message, Source.TOKEN);
    }

    public static BounceCorrelation messageId(EmailMessageEntity message) {
        return new BounceCorrelation(message, Source.MESSAGE_ID);
    }

    public static BounceCorrelation provider(EmailMessageEntity message) {
        return new BounceCorrelation(message, Source.PROVIDER);
    }

    public boolean matched() {
        return message != null;
    }

    public boolean allowsStateMutation() {
        return message != null && source == Source.TOKEN;
    }
}
