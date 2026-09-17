package com.texto.emailplatform.complaint;

import com.texto.emailplatform.email.domain.EmailMessageEntity;

/**
 * Result of mapping an untrusted complaint onto an outbound message.
 * Suppression is allowed only for token-authoritative matches.
 */
public record ComplaintCorrelation(EmailMessageEntity message, Source source) {

    public enum Source {
        TOKEN,
        MESSAGE_ID,
        PROVIDER,
        NONE
    }

    public static ComplaintCorrelation none() {
        return new ComplaintCorrelation(null, Source.NONE);
    }

    public static ComplaintCorrelation token(EmailMessageEntity message) {
        return new ComplaintCorrelation(message, Source.TOKEN);
    }

    public static ComplaintCorrelation messageId(EmailMessageEntity message) {
        return new ComplaintCorrelation(message, Source.MESSAGE_ID);
    }

    public static ComplaintCorrelation provider(EmailMessageEntity message) {
        return new ComplaintCorrelation(message, Source.PROVIDER);
    }

    public boolean matched() {
        return message != null;
    }

    public boolean allowsPolicy() {
        return message != null && source == Source.TOKEN;
    }
}
