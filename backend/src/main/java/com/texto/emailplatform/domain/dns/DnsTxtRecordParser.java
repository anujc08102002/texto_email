package com.texto.emailplatform.domain.dns;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstructs TXT RDATA returned by DNS resolver implementations.
 *
 * <p>RFC 1035 §3.3.14 defines TXT-DATA as one or more
 * {@code <character-string>} values. Multiple character-strings belonging
 * to the same TXT resource record are concatenated without inserting spaces.
 *
 * <p>Resolver implementations may expose TXT RDATA using presentation forms
 * such as:
 *
 * <pre>
 * "chunk-one" "chunk-two"
 * "chunk-one" chunk-two
 * "single-chunk"
 * </pre>
 *
 * <p>The parser reconstructs the logical TXT value while preserving
 * whitespace contained inside quoted character-strings.
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
     * <p>Multiple quoted character-strings are concatenated without spaces.
     * If a resolver returns a quoted first chunk followed by an unquoted
     * continuation, the continuation is also concatenated.
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

            /*
             * Anything between quoted chunks must be presentation
             * whitespace. Unexpected non-whitespace content is not a
             * valid continuation of the TXT RDATA.
             */
            if (!value.substring(lastEnd, matcher.start()).trim().isEmpty()) {
                return stripOuterQuotes(value);
            }

            result.append(matcher.group(1));
            lastEnd = matcher.end();
        }

        /*
         * No quoted chunks were found.
         *
         * Preserve the original behavior for plain resolver values.
         */
        if (quotedChunks == 0) {
            return stripOuterQuotes(value);
        }

        /*
         * Handle the production JNDI representation:
         *
         *     "chunk-one" chunk-two
         *
         * The unquoted suffix is a continuation of the same TXT RR.
         */
        String remainder = value.substring(lastEnd).trim();

        if (!remainder.isEmpty()) {
            /*
             * A resolver representation containing an unquoted continuation
             * after the final quoted chunk is accepted as one TXT RDATA
             * value. This is the representation observed from JNDI in
             * production for the DKIM record.
             */
            result.append(remainder);
        }

        return result.toString();
    }

    /**
     * Normalizes a reconstructed TXT value for callers that need whitespace
     * normalization.
     *
     * @param value TXT value
     * @return normalized TXT value, never {@code null}
     */
    public static String stripQuotesAndWhitespace(String value) {
        if (value == null) {
            return "";
        }

        return reconstruct(value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String stripOuterQuotes(String value) {
        if (value.length() >= 2
                && value.startsWith("\"")
                && value.endsWith("\"")) {

            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}
