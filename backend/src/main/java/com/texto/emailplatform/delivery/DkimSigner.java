package com.texto.emailplatform.delivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RFC 6376 DKIM signer using rsa-sha256 and relaxed/relaxed canonicalization.
 */
public final class DkimSigner {

    public static final String SELECTOR = "texto";

    private static final DateTimeFormatter SMTP_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private DkimSigner() {
    }

    public static String dateHeader(Instant instant) {
        return SMTP_DATE.format(instant);
    }

    public static String sign(
            String domain,
            String selector,
            PrivateKey privateKey,
            Map<String, String> headers,
            String body
    ) {
        String bh = bodyHash(body);
        List<String> signedHeaderNames = headers.keySet().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        String hTag = String.join(":", signedHeaderNames);

        String dkimValueWithoutB = "v=1; a=rsa-sha256; c=relaxed/relaxed; d=" + domain
                + "; s=" + selector
                + "; q=dns/txt; t=" + Instant.now().getEpochSecond()
                + "; h=" + hTag
                + "; bh=" + bh
                + "; b=";

        String signingInput = relaxedHeaderBlock(headers)
                + relaxedHeader("dkim-signature", dkimValueWithoutB)
                + "\r\n";
        byte[] signature = rsaSha256(privateKey, signingInput.getBytes(StandardCharsets.UTF_8));
        String b = Base64.getEncoder().encodeToString(signature);
        return foldDkim("DKIM-Signature: " + dkimValueWithoutB + b);
    }

    public static String bodyHash(String body) {
        try {
            byte[] canonical = relaxedBody(body).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash DKIM body", exception);
        }
    }

    static String relaxedBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<String> relaxed = new ArrayList<>();
        for (String line : lines) {
            String collapsed = line.replaceAll("[ \\t]+", " ").replaceAll("[ \\t]+$", "");
            relaxed.add(collapsed);
        }
        while (!relaxed.isEmpty() && relaxed.get(relaxed.size() - 1).isEmpty()) {
            relaxed.remove(relaxed.size() - 1);
        }
        if (relaxed.isEmpty()) {
            return "";
        }
        return String.join("\r\n", relaxed) + "\r\n";
    }

    private static String relaxedHeaderBlock(Map<String, String> headers) {
        return headers.entrySet().stream()
                .map(entry -> relaxedHeader(entry.getKey(), entry.getValue()) + "\r\n")
                .collect(Collectors.joining());
    }

    private static String relaxedHeader(String name, String value) {
        String unfolded = value.replaceAll("\r\n[ \t]+", " ").replaceAll("[ \t]+", " ").trim();
        return name.toLowerCase(Locale.ROOT) + ":" + unfolded;
    }

    private static byte[] rsaSha256(PrivateKey privateKey, byte[] input) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(input);
            return signature.sign();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to RSA-sign DKIM input", exception);
        }
    }

    public static boolean verify(java.security.PublicKey publicKey, String rfc822) {
        if (rfc822 == null || publicKey == null) {
            return false;
        }
        ParsedMessage parsed = parseRfc822(rfc822);
        String dkimHeader = parsed.header("dkim-signature");
        if (dkimHeader == null) {
            return false;
        }
        String unfolded = unfold(dkimHeader);
        Map<String, String> tags = parseDkimTags(unfolded);
        if (!"rsa-sha256".equalsIgnoreCase(tags.getOrDefault("a", ""))) {
            return false;
        }
        String expectedBh = tags.get("bh");
        String signatureB = tags.get("b");
        String signedHeaderList = tags.get("h");
        if (expectedBh == null || signatureB == null || signedHeaderList == null) {
            return false;
        }
        if (!expectedBh.equals(bodyHash(parsed.body()))) {
            return false;
        }
        LinkedHashMap<String, String> signedHeaders = new LinkedHashMap<>();
        for (String name : signedHeaderList.split(":")) {
            String key = name.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || "dkim-signature".equals(key)) {
                continue;
            }
            String value = parsed.header(key);
            if (value == null) {
                return false;
            }
            signedHeaders.put(key, value);
        }
        String dkimValueWithoutB = unfolded.replaceAll("b=[^;]*", "b=");
        String signingInput = relaxedHeaderBlock(signedHeaders)
                + relaxedHeader("dkim-signature", dkimValueWithoutB)
                + "\r\n";
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureB.replaceAll("\\s+", "")));
        } catch (Exception exception) {
            return false;
        }
    }

    public static boolean hasDkimSignature(String rfc822) {
        return rfc822 != null && parseRfc822(rfc822).header("dkim-signature") != null;
    }

    static ParsedMessage parseRfc822(String rfc822) {
        String normalized = rfc822.replace("\r\n", "\n").replace('\r', '\n');
        int split = normalized.indexOf("\n\n");
        String headerBlock = split >= 0 ? normalized.substring(0, split) : normalized;
        String body = split >= 0 ? normalized.substring(split + 2) : "";
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
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
            currentValue.setLength(0);
            currentValue.append(line.substring(colon + 1).trim());
        }
        flushHeader(headers, currentName, currentValue);
        return new ParsedMessage(headers, body);
    }

    private static void flushHeader(Map<String, String> headers, String name, StringBuilder value) {
        if (name == null || name.isBlank()) {
            return;
        }
        headers.put(name, value.toString());
    }

    private static String unfold(String value) {
        return value.replaceAll("\r\n[ \t]+", " ").replaceAll("[ \t]+", " ").trim();
    }

    private static Map<String, String> parseDkimTags(String value) {
        Map<String, String> tags = new LinkedHashMap<>();
        for (String part : value.split(";")) {
            String token = part.trim();
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            tags.put(token.substring(0, eq).trim().toLowerCase(Locale.ROOT), token.substring(eq + 1).trim());
        }
        return tags;
    }

    record ParsedMessage(Map<String, String> headers, String body) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private static String foldDkim(String header) {
        String[] parts = header.split("; ");
        StringBuilder folded = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String piece = parts[i] + (i == parts.length - 1 ? "" : "; ");
            if (line.length() > 0 && line.length() + piece.length() > 75) {
                folded.append(line).append("\r\n ");
                line = new StringBuilder();
            }
            line.append(piece);
        }
        folded.append(line);
        return folded.toString();
    }
}
