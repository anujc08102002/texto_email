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
 * <p>DNS resolver implementations may expose the same TXT RDATA using
 * slightly different presentation formats, for example:
 *
 * <pre>
 * "chunk-one" "chunk-two"
 * "chunk-one" chunk-two
 * "single-chunk"
 * </pre>
 *
 * <p>This class reconstructs the logical TXT value while preserving
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
     * <p>Examples:
     *
     * <pre>
     * "abc" "def"     -> abcdef
     * "abc" def       -> abcdef
     * "abc"           -> abc
     * abc             -> abc
     * "hello world"   -> hello world
     * </pre>
     *
     * @param raw resolver-provided TXT value
     * @return reconstructed TXT value, never {@code null}
     */
    public static String reconstruct(Object raw) {
        if (raw == null) {
            return "";
        }

        String value = raw.toString().trim();

        if (value.isEmpty()) {
            return "";
        }

        /*
         * First handle the normal DNS presentation form:
         *
         *     "chunk-one" "chunk-two"
         *
         * Multiple quoted character-strings are definitely separate
         * chunks belonging to the same TXT resource record.
         */
        Matcher matcher = QUOTED_CHUNKS.matcher(value);

        StringBuilder quotedResult = new StringBuilder();
        int quotedChunkCount = 0;
        int lastMatchEnd = 0;

        while (matcher.find()) {
            quotedChunkCount++;
            quotedResult.append(matcher.group(1));
            lastMatchEnd = matcher.end();
        }

        /*
         * Preserve the existing behavior for multiple quoted chunks.
         */
        if (quotedChunkCount >= 2 && onlyPresentationWhitespaceOutsideQuotes(value)) {
            return quotedResult.toString();
        }

        /*
         * Handle resolver output observed in production:
         *
         *     "chunk-one" chunk-two
         *
         * The unquoted portion is a continuation of the same TXT RDATA,
         * not a separate TXT resource record.
         */
        if (quotedChunkCount == 1) {
            String prefix = matcherStartContent(value, matcherStart(value));
            String suffix = value.substring(lastMatchEnd).trim();

            if (isSingleLeadingQuotedChunk(value, matcherStart(value))
                    && !suffix.isEmpty()) {

                return prefix + suffix;
            }

            /*
             * A single fully quoted TXT value:
             *
             *     "hello world"
             */
            if (looksFullyQuoted(value)) {
                return quotedResult.toString();
            }
        }

        /*
         * Preserve the original fallback behavior for plain TXT values
         * and unexpected resolver representations.
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

    /**
     * Returns the start index of the first quoted chunk.
     */
    private static int matcherStart(String value) {
        Matcher matcher = QUOTED_CHUNKS.matcher(value);
        return matcher.find() ? matcher.start() : -1;
    }

    /**
     * Returns the content before the quoted chunk.
     */
    private static String matcherStartContent(String value, int start) {
        if (start <= 0) {
            return "";
        }

        return value.substring(0, start).trim();
    }

    /**
     * Determines whether the supplied value consists of one leading quoted
     * chunk followed by an unquoted continuation.
     */
    private static boolean isSingleLeadingQuotedChunk(String value, int start) {
        if (start != 0) {
            return false;
        }

        int firstQuoteEnd = value.indexOf('"', 1);

        return firstQuoteEnd > 0
                && firstQuoteEnd < value.length() - 1;
    }

    /**
     * Returns true when whitespace outside quoted chunks is only presentation
     * whitespace.
     */
    private static boolean onlyPresentationWhitespaceOutsideQuotes(String value) {
        Matcher matcher = QUOTED_CHUNKS.matcher(value);

        int previousEnd = 0;

        while (matcher.find()) {
            String between = value.substring(previousEnd, matcher.start());

            if (!between.trim().isEmpty()) {
                return false;
            }

            previousEnd = matcher.end();
        }

        return value.substring(previousEnd).trim().isEmpty();
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
