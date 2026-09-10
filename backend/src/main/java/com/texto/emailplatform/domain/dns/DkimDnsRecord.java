package com.texto.emailplatform.domain.dns;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DKIM TXT record at {@code <selector>._domainkey.<domain>} (RFC 6376 §3.6.2).
 */
public final class DkimDnsRecord {

    private DkimDnsRecord() {
    }

    public static String ownerName(String selector, String domain) {
        return selector + "._domainkey." + domain;
    }

    public static String generate(String publicKey) {
        return "v=DKIM1; k=rsa; p=" + (publicKey == null ? "" : publicKey.replaceAll("\\s+", ""));
    }

    public static MatchResult match(List<String> txtRecords, String expectedPublicKey) {
        if (txtRecords == null || txtRecords.isEmpty()) {
            return MatchResult.missing();
        }
        String expected = normalizeBase64(expectedPublicKey);
        Parsed found = null;
        for (String raw : txtRecords) {
            Parsed parsed = parse(raw);
            if (parsed == null) {
                continue;
            }
            if (found != null) {
                return MatchResult.multiple();
            }
            found = parsed;
        }
        if (found == null) {
            return MatchResult.missing();
        }
        if (found.publicKey().isEmpty()) {
            return MatchResult.revoked();
        }
        String keyType = found.tags().get("k");
        if (keyType != null && !keyType.equalsIgnoreCase("rsa")) {
            return MatchResult.mismatch();
        }
        if (!expected.equals(found.publicKey())) {
            return MatchResult.mismatch();
        }
        return MatchResult.matched();
    }

    public static Parsed parse(String value) {
        String reconstructed = DnsTxtRecordParser.reconstruct(value).trim();
        if (reconstructed.isEmpty()) {
            return null;
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (String part : reconstructed.split(";")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            tags.put(token.substring(0, eq).trim().toLowerCase(Locale.ROOT), token.substring(eq + 1).trim());
        }
        String version = tags.get("v");
        if (version != null && !version.equalsIgnoreCase("DKIM1")) {
            return null;
        }
        if (!tags.containsKey("p")) {
            return null;
        }
        return new Parsed(normalizeBase64(tags.get("p")), tags);
    }

    public static String normalizeBase64(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "");
    }

    public record Parsed(String publicKey, Map<String, String> tags) {
    }

    public enum Status {
        MATCHED,
        MISSING,
        MULTIPLE,
        REVOKED,
        MISMATCH
    }

    public record MatchResult(Status status) {
        static MatchResult matched() {
            return new MatchResult(Status.MATCHED);
        }

        static MatchResult missing() {
            return new MatchResult(Status.MISSING);
        }

        static MatchResult multiple() {
            return new MatchResult(Status.MULTIPLE);
        }

        static MatchResult revoked() {
            return new MatchResult(Status.REVOKED);
        }

        static MatchResult mismatch() {
            return new MatchResult(Status.MISMATCH);
        }

        public boolean isMatched() {
            return status == Status.MATCHED;
        }
    }
}
