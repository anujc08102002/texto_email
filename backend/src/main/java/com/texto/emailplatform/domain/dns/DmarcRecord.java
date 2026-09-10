package com.texto.emailplatform.domain.dns;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DMARC TXT helper (RFC 7489).
 *
 * <p>Publishing this record does <strong>not</strong> mean the application evaluated DMARC.
 * Receiving MTAs evaluate alignment of the From domain against DKIM {@code d=} and/or
 * SPF-authenticated MAIL FROM. This class only generates and presence-checks the policy record.
 */
public final class DmarcRecord {

    public static final String VERSION = "DMARC1";
    private static final Set<String> POLICIES = Set.of("none", "quarantine", "reject");
    private static final Set<String> ALIGNMENT = Set.of("r", "s");

    private DmarcRecord() {
    }

    public static String generate(DmarcSettings settings) {
        DmarcSettings safe = settings == null ? DmarcSettings.defaults() : settings;
        StringBuilder record = new StringBuilder("v=DMARC1; p=").append(safe.normalizedPolicy());
        append(record, "sp", safe.subdomainPolicy());
        append(record, "rua", safe.rua());
        append(record, "ruf", safe.ruf());
        append(record, "adkim", safe.dkimAlignment());
        append(record, "aspf", safe.spfAlignment());
        if (safe.percentage() != null) {
            record.append("; pct=").append(safe.percentage());
        }
        return record.toString();
    }

    public static MatchResult match(List<String> txtRecords, DmarcSettings expected) {
        if (txtRecords == null || txtRecords.isEmpty()) {
            return MatchResult.missing();
        }
        Parsed found = null;
        for (String raw : txtRecords) {
            String reconstructed = DnsTxtRecordParser.reconstruct(raw).trim();
            Parsed parsed = parse(raw);
            if (parsed == null) {
                if (looksLikeDmarc(reconstructed)) {
                    return MatchResult.malformed();
                }
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
        if (!VERSION.equalsIgnoreCase(found.version()) || !POLICIES.contains(found.policy())) {
            return MatchResult.malformed();
        }
        String expectedPolicy = expected == null ? "none" : expected.normalizedPolicy();
        if (!expectedPolicy.equals(found.policy())) {
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
                return null;
            }
            String name = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String rawValue = token.substring(eq + 1).trim();
            tags.put(name, rawValue);
        }
        String version = tags.get("v");
        if (version == null || !VERSION.equalsIgnoreCase(version)) {
            return null;
        }
        String policy = tags.get("p") == null ? "" : tags.get("p").toLowerCase(Locale.ROOT);
        if (!POLICIES.contains(policy)) {
            return null;
        }
        if (invalidAlignment(tags.get("adkim")) || invalidAlignment(tags.get("aspf"))) {
            return null;
        }
        if (tags.containsKey("pct") && parsePct(tags.get("pct")) == null) {
            return null;
        }
        return new Parsed(VERSION, policy, tags);
    }

    private static boolean looksLikeDmarc(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String first = value.split("[;\\s]")[0];
        return "v=dmarc1".equalsIgnoreCase(first);
    }

    private static boolean invalidAlignment(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !ALIGNMENT.contains(value.toLowerCase(Locale.ROOT));
    }

    private static Integer parsePct(String value) {
        try {
            int pct = Integer.parseInt(value.trim());
            if (pct < 0 || pct > 100) {
                return null;
            }
            return pct;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static void append(StringBuilder record, String tag, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        record.append("; ").append(tag).append("=").append(value.trim());
    }

    public record Parsed(String version, String policy, Map<String, String> tags) {
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
}
