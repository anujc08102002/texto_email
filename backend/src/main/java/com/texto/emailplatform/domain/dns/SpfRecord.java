package com.texto.emailplatform.domain.dns;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * SPF presence/match helper (RFC 7208).
 *
 * <p>This is <strong>not</strong> a recursive SPF evaluator. It does not walk
 * {@code include}/{@code a}/{@code mx} mechanisms and therefore does not enforce
 * the RFC 7208 §4.6.4 limit of 10 DNS-querying mechanisms. Receiving MTAs perform
 * that evaluation. We only check that the published TXT set contains exactly one
 * {@code v=spf1} record whose directives match the record we publish.
 */
public final class SpfRecord {

    public static final String VERSION = "v=spf1";

    private SpfRecord() {
    }

    public static String generate(String includeDomain, String allQualifier) {
        return generate(includeDomain, allQualifier, null);
    }

    public static String generate(String includeDomain, String allQualifier, String ip4) {
        String qualifier = normalizeQualifier(allQualifier);
        StringBuilder record = new StringBuilder(VERSION);
        if (ip4 != null && !ip4.isBlank()) {
            record.append(" ip4:").append(ip4.trim());
        } else {
            String include = includeDomain == null ? "" : includeDomain.trim().toLowerCase(Locale.ROOT);
            record.append(" include:").append(include);
        }
        return record.append(' ').append(qualifier).append("all").toString();
    }

    public static MatchResult match(List<String> txtRecords, String expectedInclude, String allQualifier) {
        return match(txtRecords, expectedInclude, allQualifier, null);
    }

    public static MatchResult match(List<String> txtRecords, String expectedInclude, String allQualifier, String ip4) {
        List<String> spfRecords = new ArrayList<>();
        if (txtRecords != null) {
            for (String raw : txtRecords) {
                String reconstructed = DnsTxtRecordParser.reconstruct(raw);
                if (isSpfRecord(reconstructed)) {
                    spfRecords.add(reconstructed);
                }
            }
        }
        if (spfRecords.isEmpty()) {
            return MatchResult.missing();
        }
        if (spfRecords.size() > 1) {
            // RFC 7208 §3.2 / §4.5: two or more SPF records at the owner name is permerror.
            return MatchResult.multiple();
        }
        Parsed parsed = parse(spfRecords.get(0));
        if (parsed == null) {
            return MatchResult.malformed();
        }
        String expected = generate(expectedInclude, allQualifier, ip4);
        Parsed want = parse(expected);
        if (want == null || !parsed.equals(want)) {
            return MatchResult.mismatch();
        }
        return MatchResult.matched();
    }

    public static boolean isSpfRecord(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = DnsTxtRecordParser.reconstruct(value).trim();
        String[] tokens = trimmed.split("\\s+");
        return tokens.length > 0 && VERSION.equalsIgnoreCase(tokens[0]);
    }

    static Parsed parse(String value) {
        String reconstructed = DnsTxtRecordParser.reconstruct(value).trim();
        String[] tokens = reconstructed.split("\\s+");
        if (tokens.length == 0 || !VERSION.equalsIgnoreCase(tokens[0])) {
            return null;
        }
        List<String> terms = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            String term = tokens[i].trim();
            if (term.isEmpty()) {
                continue;
            }
            terms.add(normalizeTerm(term));
        }
        if (terms.isEmpty()) {
            return null;
        }
        return new Parsed(terms);
    }

    private static String normalizeTerm(String term) {
        String lower = term.toLowerCase(Locale.ROOT);
        if (lower.startsWith("include:")) {
            return "include:" + lower.substring("include:".length());
        }
        if (lower.startsWith("ip4:")) {
            return "ip4:" + lower.substring("ip4:".length());
        }
        return lower;
    }

    private static String normalizeQualifier(String qualifier) {
        if (qualifier == null || qualifier.isBlank()) {
            return "~";
        }
        String value = qualifier.trim();
        if (value.length() == 1 && "+-?~".contains(value)) {
            return value;
        }
        return "~";
    }

    public enum Status {
        MATCHED,
        MISSING,
        MULTIPLE,
        MALFORMED,
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

        static MatchResult malformed() {
            return new MatchResult(Status.MALFORMED);
        }

        static MatchResult mismatch() {
            return new MatchResult(Status.MISMATCH);
        }

        public boolean isMatched() {
            return status == Status.MATCHED;
        }
    }

    record Parsed(List<String> terms) {
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Parsed parsed)) {
                return false;
            }
            return Objects.equals(terms, parsed.terms);
        }

        @Override
        public int hashCode() {
            return Objects.hash(terms);
        }
    }
}
