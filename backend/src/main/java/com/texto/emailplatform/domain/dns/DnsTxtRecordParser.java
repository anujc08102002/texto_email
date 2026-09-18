package com.texto.emailplatform.domain.dns;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstructs TXT RDATA.
 *
 * <p>RFC 1035 §3.3.14: TXT-DATA is one or more {@code <character-string>}. RFC 7208 §3.3
 * (and DKIM RFC 6376) require those strings in a <em>single</em> TXT RR to be concatenated
 * without adding spaces. Separate TXT RRs remain separate records.
 */
public final class DnsTxtRecordParser {

    private static final Pattern QUOTED_CHUNKS =
            Pattern.compile("\"([^\"]*)\"");

    private DnsTxtRecordParser() {
    }

    /**
     * Reconstructs one TXT RR from a resolver attribute value.
     * Handles quoted multi-string forms such as {@code "v=DKIM1; k=rsa; p=" "MIIB..."}.
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
        List<String> chunks = new ArrayList<>();

        while (matcher.find()) {
            chunks.add(matcher.group(1));
        }

        if (chunks.size() >= 2) {
            return String.join("", chunks);
        }

        if (chunks.size() == 1 && looksFullyQuoted(value)) {
            return chunks.get(0);
        }

        return stripOuterQuotes(value);
    }

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
