package com.texto.emailplatform.domain.dns;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstructs TXT RDATA.
 *
 * <p>RFC 1035 §3.3.14: TXT-DATA consists of one or more
 * {@code <character-string>} values. Multiple character-strings belonging
 * to the same TXT resource record are concatenated without inserting spaces.
 *
 * <p>Resolver implementations may expose TXT RDATA in presentation forms
 * such as {@code "chunk-one" "chunk-two"} or {@code "chunk-one" chunk-two}.
 */
public final class DnsTxtRecordParser {

    private static final Pattern QUOTED_CHUNKS =
            Pattern.compile("\"([^\"]*)\"");

    private DnsTxtRecordParser() {
        // Utility class.
    }

    /**
     * Reconstructs one TXT resource record from a resolver attribute value.
     *
     * @param raw resolver-provided TXT value
     * @return reconstructed logical TXT value, never {@code null}
     */
    public static String reconstruct(Object raw) {
        if (raw == null) {
            return "";
        }

        String value = raw.toString().trim();

        if (value.isEmpty()) {
            return "";
        }

        Matcher matcher = QUOTED_CHUNKS.matcher(value);

        StringBuilder result = new StringBuilder();
        int quotedChunks = 0;
        int lastEnd = 0;

        while (matcher.find()) {
            quotedChunks++;

            result.append(matcher.group(1));
            lastEnd = matcher.end();
        }

        /*
         * Standard presentation form:
         *
         *     "chunk-one" "chunk-two"
         *
         * Concatenate all quoted chunks without spaces.
         */
        if (quotedChunks >= 2) {
            return result.toString();
        }

        /*
         * Production JNDI form observed with this application:
         *
         *     "chunk-one" chunk-two
         *
         * The text following the quoted chunk is a continuation of the
         * same TXT resource record.
         */
        if (quotedChunks == 1
                && value.startsWith("\"")
                && lastEnd < value.length()) {

            String remainder = value.substring(lastEnd).trim();

            if (!remainder.isEmpty()) {
                return result.append(remainder).toString();
            }
        }

        /*
         * Single quoted TXT value:
         *
         *     "plain text"
         */
        if (quotedChunks == 1 && looksFullyQuoted(value)) {
            return result.toString();
        }

        /*
         * Plain/unexpected TXT representation.
         */
        return stripOuterQuotes(value);
    }

    /**
     * Normalizes a TXT value for callers that need whitespace normalization
     * after TXT reconstruction.
     */
    public static String stripQuotesAndWhitespace(String value) {
        if (value == null) {
            return "";
        }

        return reconstruct(value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean looksFullyQuoted(String value) {
        return value.startsWith("\"")
                && value.endsWith("\"")
                && value.indexOf('"', 1) == value.length() - 1;
    }

    private static String stripOuterQuotes(String value) {
        String current = value;

        if (current.length() >= 2
                && current.startsWith("\"")
                && current.endsWith("\"")) {

            current = current.substring(1, current.length() - 1);
        }

        return current;
    }
}
