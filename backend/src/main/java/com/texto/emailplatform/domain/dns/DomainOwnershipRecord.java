package com.texto.emailplatform.domain.dns;

import java.util.List;
import java.util.Locale;

/**
 * Dedicated ownership TXT (not mixed into SPF/DKIM/DMARC).
 *
 * <p>Owner: {@code texto-verify.<domain>}
 * Value: {@code texto-domain-verification=<token>}
 */
public final class DomainOwnershipRecord {

    public static final String HOST_PREFIX = "texto-verify.";
    public static final String VALUE_PREFIX = "texto-domain-verification=";

    private DomainOwnershipRecord() {
    }

    public static String ownerName(String domain) {
        return HOST_PREFIX + domain;
    }

    public static String generate(String token) {
        return VALUE_PREFIX + token;
    }

    public static MatchResult match(List<String> txtRecords, String expectedToken) {
        if (txtRecords == null || txtRecords.isEmpty()) {
            return MatchResult.missing();
        }
        String expected = expectedToken == null ? "" : expectedToken.trim().toLowerCase(Locale.ROOT);
        boolean sawOwnership = false;
        for (String raw : txtRecords) {
            String reconstructed = DnsTxtRecordParser.reconstruct(raw).trim();
            String token = extractToken(reconstructed);
            if (token == null) {
                continue;
            }
            sawOwnership = true;
            if (expected.equals(token.toLowerCase(Locale.ROOT))) {
                return MatchResult.matched();
            }
        }
        return sawOwnership ? MatchResult.mismatch() : MatchResult.missing();
    }

    public static String extractToken(String value) {
        if (value == null) {
            return null;
        }
        String reconstructed = DnsTxtRecordParser.reconstruct(value).trim();
        String[] parts = reconstructed.split("\\s+");
        for (String part : parts) {
            if (part.toLowerCase(Locale.ROOT).startsWith(VALUE_PREFIX)) {
                return part.substring(VALUE_PREFIX.length()).trim();
            }
        }
        return null;
    }

    public record MatchResult(Status status) {
        static MatchResult matched() {
            return new MatchResult(Status.MATCHED);
        }

        static MatchResult missing() {
            return new MatchResult(Status.MISSING);
        }

        static MatchResult mismatch() {
            return new MatchResult(Status.MISMATCH);
        }

        public boolean isMatched() {
            return status == Status.MATCHED;
        }
    }

    public enum Status {
        MATCHED,
        MISSING,
        MISMATCH
    }
}
