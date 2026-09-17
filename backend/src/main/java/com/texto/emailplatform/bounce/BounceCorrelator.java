package com.texto.emailplatform.bounce;

import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps an untrusted DSN onto an outbound message.
 *
 * <p>Priority: bounce token (authoritative) → Original-Envelope-ID → RFC 822 Message-ID
 * → provider message ID. Tenant is resolved from the matched database row, never from DSN fields.
 * Message-ID / provider fallbacks persist the event but do not authorize state mutation.
 */
@Component
public class BounceCorrelator {

    private final EmailMessageRepository emailMessageRepository;
    private final BounceTokenExtractor tokenExtractor;

    public BounceCorrelator(EmailMessageRepository emailMessageRepository, BounceTokenExtractor tokenExtractor) {
        this.emailMessageRepository = emailMessageRepository;
        this.tokenExtractor = tokenExtractor;
    }

    public BounceCorrelation correlate(ParsedDsn dsn, DsnRecipient recipient, String envelopeRecipient) {
        Optional<String> token = extractToken(dsn, recipient, envelopeRecipient);
        if (token.isPresent()) {
            return emailMessageRepository.findByBounceCorrelationToken(token.get())
                    .map(BounceCorrelation::token)
                    .orElseGet(BounceCorrelation::none);
        }
        if (dsn == null) {
            return BounceCorrelation.none();
        }
        Optional<EmailMessageEntity> byMessageId = uniqueRfc822(dsn.originalMessageId());
        if (byMessageId.isPresent()) {
            return BounceCorrelation.messageId(byMessageId.get());
        }
        Optional<EmailMessageEntity> byEnvelope = uniqueRfc822(dsn.originalEnvelopeId())
                .or(() -> uniqueProvider(dsn.originalEnvelopeId()));
        if (byEnvelope.isPresent()) {
            return uniqueRfc822(dsn.originalEnvelopeId()).isPresent()
                    ? BounceCorrelation.messageId(byEnvelope.get())
                    : BounceCorrelation.provider(byEnvelope.get());
        }
        return uniqueProvider(dsn.originalMessageId())
                .map(BounceCorrelation::provider)
                .orElseGet(BounceCorrelation::none);
    }

    Optional<String> extractToken(ParsedDsn dsn, DsnRecipient recipient, String envelopeRecipient) {
        String originalRecipient = recipient == null ? null : recipient.originalRecipient();
        String finalRecipient = recipient == null ? null : recipient.finalRecipient();
        return tokenExtractor.extractFirst(
                envelopeRecipient,
                dsn == null ? null : dsn.originalEnvelopeId(),
                dsn == null ? null : dsn.originalSender(),
                dsn == null ? null : dsn.reportTo(),
                originalRecipient,
                finalRecipient
        );
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

    public static String normalizeMessageId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        int lt = trimmed.indexOf('<');
        int gt = trimmed.lastIndexOf('>');
        if (lt >= 0 && gt > lt) {
            trimmed = trimmed.substring(lt, gt + 1);
        } else if (!trimmed.startsWith("<")) {
            trimmed = "<" + trimmed + ">";
        }
        return trimmed;
    }

    private static String stripBrackets(String messageId) {
        if (messageId.length() >= 2 && messageId.startsWith("<") && messageId.endsWith(">")) {
            return messageId.substring(1, messageId.length() - 1);
        }
        return messageId;
    }
}
