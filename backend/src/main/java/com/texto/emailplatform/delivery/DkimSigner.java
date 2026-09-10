package com.texto.emailplatform.delivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RFC 6376 DKIM signer (rsa-sha256, relaxed/simple). Used before MTA submission.
 */
public final class DkimSigner {

    public static final String HEADER = "DKIM-Signature";

    private DkimSigner() {
    }

    public static byte[] sign(
            byte[] rfc822,
            String domain,
            String selector,
            PrivateKey privateKey,
            Instant timestamp
    ) {
        ParsedMessage parsed = parse(rfc822);
        String bodyHash = Base64.getEncoder().encodeToString(sha256(simpleBody(parsed.body)));
        List<String> signed = List.of("from", "to", "subject", "date", "message-id", "mime-version", "content-type");
        String headerList = String.join(":", signed);
        String dkimWithoutB = "v=1; a=rsa-sha256; c=relaxed/simple; d=" + domain
                + "; s=" + selector
                + "; t=" + timestamp.getEpochSecond()
                + "; bh=" + bodyHash
                + "; h=" + headerList
                + "; b=";
        String toSign = relaxedSignedHeaders(parsed.headers, signed) + relaxedHeader("dkim-signature", dkimWithoutB);
        String signature = Base64.getEncoder().encodeToString(rsaSha256(privateKey, toSign.getBytes(StandardCharsets.UTF_8)));
        String dkim = fold(dkimWithoutB + signature);
        StringBuilder out = new StringBuilder();
        out.append("DKIM-Signature: ").append(dkim).append("\r\n");
        out.append(new String(rfc822, StandardCharsets.UTF_8));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static boolean verify(byte[] rfc822, PublicKey publicKey) {
        ParsedMessage parsed = parse(rfc822);
        String dkim = headerValue(parsed.headers, "dkim-signature");
        if (dkim == null) {
            return false;
        }
        Map<String, String> tags = parseTags(dkim);
        String bodyHash = tags.get("bh") == null ? null : tags.get("bh").replaceAll("\\s+", "");
        String expected = Base64.getEncoder().encodeToString(sha256(simpleBody(parsed.body)));
        if (bodyHash == null || !bodyHash.equals(expected)) {
            return false;
        }
        String b = tags.get("b");
        if (b == null) {
            return false;
        }
        String headerList = tags.getOrDefault("h", "").replaceAll("\\s+", "");
        List<String> signed = new ArrayList<>();
        for (String name : headerList.split(":")) {
            if (!name.isBlank()) {
                signed.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        String dkimWithoutB = dkimHeaderWithoutSignature(tags);
        String toSign = relaxedSignedHeaders(parsed.headers, signed) + relaxedHeader("dkim-signature", dkimWithoutB);
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(toSign.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(b.replaceAll("\\s+", "")));
        } catch (Exception exception) {
            return false;
        }
    }

    public static boolean hasSignature(byte[] rfc822) {
        String text = new String(rfc822, StandardCharsets.UTF_8);
        return text.regionMatches(true, 0, "DKIM-Signature:", 0, "DKIM-Signature:".length())
                || text.toLowerCase(Locale.ROOT).contains("\r\ndkim-signature:");
    }

    static byte[] simpleBody(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        while (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            return "\r\n".getBytes(StandardCharsets.UTF_8);
        }
        return (text.replace("\n", "\r\n") + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static ParsedMessage parse(byte[] rfc822) {
        String text = new String(rfc822, StandardCharsets.UTF_8);
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int split = normalized.indexOf("\n\n");
        String headerBlock = split < 0 ? normalized : normalized.substring(0, split);
        String bodyBlock = split < 0 ? "" : normalized.substring(split + 2);
        LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>();
        String currentName = null;
        StringBuilder currentValue = new StringBuilder();
        for (String line : headerBlock.split("\n", -1)) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (currentName != null) {
                    currentValue.append(' ').append(line.trim());
                }
                continue;
            }
            flushHeader(headers, currentName, currentValue);
            int colon = line.indexOf(':');
            if (colon <= 0) {
                currentName = null;
                currentValue.setLength(0);
                continue;
            }
            currentName = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            currentValue = new StringBuilder(line.substring(colon + 1).trim());
        }
        flushHeader(headers, currentName, currentValue);
        byte[] body = bodyBlock.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        return new ParsedMessage(headers, body);
    }

    private static void flushHeader(Map<String, List<String>> headers, String name, StringBuilder value) {
        if (name == null || name.isBlank()) {
            return;
        }
        headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value.toString());
    }

    private static String relaxedSignedHeaders(Map<String, List<String>> headers, List<String> signed) {
        StringBuilder builder = new StringBuilder();
        for (String name : signed) {
            String value = headerValue(headers, name);
            if (value == null) {
                continue;
            }
            builder.append(relaxedHeader(name, value)).append("\r\n");
        }
        return builder.toString();
    }

    private static String relaxedHeader(String name, String value) {
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return name.toLowerCase(Locale.ROOT) + ":" + collapsed;
    }

    private static String headerValue(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    private static Map<String, String> parseTags(String dkim) {
        Map<String, String> tags = new LinkedHashMap<>();
        for (String part : dkim.split(";")) {
            String token = part.trim();
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            tags.put(token.substring(0, eq).trim().toLowerCase(Locale.ROOT), token.substring(eq + 1).trim());
        }
        return tags;
    }

    private static String dkimHeaderWithoutSignature(Map<String, String> tags) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(tag.getKey()).append("=");
            if (!"b".equals(tag.getKey())) {
                builder.append(tag.getValue().replaceAll("\\s+", ""));
            }
        }
        return builder.toString();
    }

    private static String fold(String value) {
        if (value.length() <= 70) {
            return value;
        }
        StringBuilder folded = new StringBuilder();
        int index = 0;
        while (index < value.length()) {
            int end = Math.min(index + 70, value.length());
            if (index > 0) {
                folded.append("\r\n ");
            }
            folded.append(value, index, end);
            index = end;
        }
        return folded.toString();
    }

    private static byte[] rsaSha256(PrivateKey privateKey, byte[] content) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(content);
            return signature.sign();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign DKIM", exception);
        }
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ParsedMessage(LinkedHashMap<String, List<String>> headers, byte[] body) {
    }
}
