package com.texto.emailplatform.domain.dns;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstructs TXT RDATA returned by DNS resolver providers.
 *
 * <p>RFC 1035 §3.3.14 defines TXT-DATA as one or more {@code <character-string>}
 * values. Multiple character-strings belonging to the same TXT resource record
 * must be concatenated without inserting spaces.
 *
 * <p>Resolver implementations may return TXT RDATA in presentation forms such as:
 *
 * <pre>
 * "chunk-one" "chunk-two"
 * "chunk-one" chunk-two
 * "single-chunk"
 * </pre>
 *
 * <p>This parser reconstructs the logical TXT value while preserving whitespace
 * that occurs inside quoted character-strings.
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
     * "abc" "def"       -> abcdef
     * "abc" def         -> abcdef
     * "abc"             -> abc
     * abc               -> abc
     * "hello world"     -> hello world
     * </pre>
     *
     * <p>Whitespace outside quoted chunks is DNS presentation syntax and is
     * therefore removed. Whitespace inside quoted chunks is preserved because
     * it is part of the TXT character-string.
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

        StringBuilder result = new StringBuilder(value.length());

        boolean insideQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);

            /*
             * Preserve the character following an escape sequence.
             *
             * DNS presentation format may escape characters using '\'.
             * The escape character itself is presentation syntax and should
             * not become part of the reconstructed TXT value.
             */
            if (escaped) {
                result.append(current);
                escaped = false;
                continue;
            }

            if (current == '\\') {
                escaped = true;
                continue;
            }

            /*
             * Quotes delimit DNS presentation-format character-strings.
             * They are not part of the logical TXT value.
             */
            if (current == '"') {
                insideQuotes = !insideQuotes;
                continue;
            }

            /*
             * Whitespace outside quoted chunks separates character-strings
             * in DNS presentation format. It must not be inserted into the
             * reconstructed TXT value.
             *
             * Whitespace inside quotes is actual TXT data and is preserved.
             */
            if (Character.isWhitespace(current) && !insideQuotes) {
                continue;
            }

            result.append(current);
        }

        /*
         * A trailing escape character is malformed presentation syntax.
         * We deliberately do not append it because '\' is only an escape
         * marker in this representation.
         */
        return result.toString();
    }

    /**
     * Normalizes a TXT value for comparisons where insignificant whitespace
     * between DNS presentation fragments should be ignored.
     *
     * <p>Whitespace that is part of the logical TXT value is normalized to a
     * single space after TXT reconstruction.
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
}
