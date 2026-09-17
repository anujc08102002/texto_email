package com.texto.emailplatform.complaint;

import com.texto.emailplatform.bounce.BounceCorrelator;
import com.texto.emailplatform.bounce.BounceCorrelationToken;
import com.texto.emailplatform.bounce.BounceTokenExtractor;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps an untrusted complaint onto an outbound message.
 *
 * <p>Priority: opaque correlation token → unique provider message id → unique RFC 822 Message-ID.
 * Tenant is resolved from the matched row. Recipient address alone never correlates.
 * Message-ID / provider fallbacks persist the event but do not authorize suppression.
 */
@Component
public class ComplaintCorrelator {

    private final EmailMessageRepository emailMessageRepository;
    private final BounceTokenExtractor tokenExtractor;

    public ComplaintCorrelator(
            EmailMessageRepository emailMessageRepository,
            BounceTokenExtractor tokenExtractor
    ) {
        this.emailMessageRepository = emailMessageRepository;
        this.tokenExtractor = tokenExtractor;
    }

    public ComplaintCorrelation correlate(ComplaintIngestionRequest request) {
        Optional<String> token = extractToken(request);
        if (token.isPresent()) {
            return emailMessageRepository.findByBounceCorrelationToken(token.get())
                    .map(ComplaintCorrelation::token)
                    .orElseGet(ComplaintCorrelation::none);
        }
        if (request == null) {
            return ComplaintCorrelation.none();
        }
        Optional<EmailMessageEntity> byProvider = uniqueProvider(request.providerMessageId());
        if (byProvider.isPresent()) {
            return ComplaintCorrelation.provider(byProvider.get());
        }
        return uniqueRfc822(request.messageId())
                .map(ComplaintCorrelation::messageId)
                .orElseGet(ComplaintCorrelation::none);
    }

    Optional<String> extractToken(ComplaintIngestionRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        return tokenExtractor.extractFirst(request.correlationToken())
                .or(() -> Optional.ofNullable(BounceCorrelationToken.normalize(request.correlationToken())));
    }

    private Optional<EmailMessageEntity> uniqueRfc822(String rawId) {
        String normalized = BounceCorrelator.normalizeMessageId(rawId);
        if (normalized == null) {
            return Optional.empty();
        }
        var matches = emailMessageRepository.findByRfc822MessageId(normalized);
        if (matches.size() == 1) {
            return Optional.of(matches.getFirst());
        }
        String stripped = stripBrackets(normalized);
        if (!stripped.equals(normalized)) {
            var alt = emailMessageRepository.findByRfc822MessageId(stripped);
            if (alt.size() == 1) {
                return Optional.of(alt.getFirst());
            }
        }
        return Optional.empty();
    }

    private Optional<EmailMessageEntity> uniqueProvider(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        var matches = emailMessageRepository.findByProviderMessageId(rawId.trim());
        if (matches.size() == 1) {
            return Optional.of(matches.getFirst());
        }
        return Optional.empty();
    }

    private static String stripBrackets(String messageId) {
        if (messageId.length() >= 2 && messageId.startsWith("<") && messageId.endsWith(">")) {
            return messageId.substring(1, messageId.length() - 1);
        }
        return messageId;
    }
}
