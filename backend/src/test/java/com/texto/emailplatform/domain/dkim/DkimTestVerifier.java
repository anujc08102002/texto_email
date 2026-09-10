package com.texto.emailplatform.domain.dkim;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Independent DKIM verifier for RAW RFC822 messages, implemented from scratch for tests (separate
 * relaxed-canonicalization + JCA {@code SHA256withRSA}). Used to verify what the real delivery
 * engine actually sent to Mailpit, without calling any production signing code.
 */
public final class DkimTestVerifier {

    private DkimTestVerifier() {
    }

    public static PublicKey rsaPublicKeyFromBase64Spki(String base64Spki) throws Exception {
        byte[] der = Base64.getDecoder().decode(base64Spki);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    /** Parses the DKIM-Signature header value into its tag map. */
    public static Map<String, String> parseTags(String dkimHeaderValue) {
        String unfolded = dkimHeaderValue.replaceAll("\\r?\\n[ \\t]+", " ");
        Map<String, String> tags = new LinkedHashMap<>();
        for (String part : unfolded.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq < 0) {
                continue;
            }
            tags.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
        }
        return tags;
    }

    public static String dkimSignatureHeaderValue(String rawMessage) {
        for (String[] header : parseHeaders(rawMessage)) {
            if (header[0].equalsIgnoreCase("DKIM-Signature")) {
                return header[1];
            }
        }
        return null;
    }

    /** Verifies the DKIM signature contained in a raw RFC822 message against {@code publicKey}. */
    public static boolean verifyRaw(String rawMessage, PublicKey publicKey) {
        String normalized = rawMessage.replace("\r\n", "\n").replace("\r", "\n");
        int sep = normalized.indexOf("\n\n");
        if (sep < 0) {
            return false;
        }
        String body = normalized.substring(sep + 2);
        List<String[]> headers = parseHeaders(rawMessage);

        String dkim = null;
        for (String[] header : headers) {
            if (header[0].equalsIgnoreCase("DKIM-Signature")) {
                dkim = header[1];
                break;
            }
        }
        if (dkim == null) {
            return false;
        }
        Map<String, String> tags = parseTags(dkim);

        // Body hash.
        String expectedBh = base64(sha256(relaxedBody(body).getBytes(StandardCharsets.UTF_8)));
        if (!expectedBh.equals(tags.get("bh"))) {
            return false;
        }

        // Signing input from h=.
        StringBuilder signingInput = new StringBuilder();
        for (String name : tags.get("h").split(":")) {
            String value = firstHeaderValue(headers, name.trim());
            if (value == null) {
                return false;
            }
            signingInput.append(relaxedHeader(name.trim(), value)).append("\r\n");
        }
        int bIdx = dkim.lastIndexOf("b=");
        signingInput.append(relaxedHeader("DKIM-Signature", dkim.substring(0, bIdx + 2)));

        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingInput.toString().getBytes(StandardCharsets.US_ASCII));
            return verifier.verify(Base64.getDecoder().decode(tags.get("b").replaceAll("\\s", "")));
        } catch (Exception exception) {
            return false;
        }
    }

    /** Parses headers with folding (continuation lines beginning with WSP) into ordered name/value pairs. */
    static List<String[]> parseHeaders(String rawMessage) {
        String normalized = rawMessage.replace("\r\n", "\n").replace("\r", "\n");
        int sep = normalized.indexOf("\n\n");
        String headerBlock = sep < 0 ? normalized : normalized.substring(0, sep);
        List<String[]> headers = new ArrayList<>();
        StringBuilder current = null;
        String currentName = null;
        for (String line : headerBlock.split("\n", -1)) {
            if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                if (current != null) {
                    current.append("\n").append(line); // preserve folding for relaxed canonicalization
                }
                continue;
            }
            if (current != null) {
                headers.add(new String[]{currentName, current.toString()});
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                current = null;
                currentName = null;
                continue;
            }
            currentName = line.substring(0, colon).trim();
            current = new StringBuilder(line.substring(colon + 1));
            // Trim a single leading space after the colon.
            if (current.length() > 0 && current.charAt(0) == ' ') {
                current.deleteCharAt(0);
            }
        }
        if (current != null) {
            headers.add(new String[]{currentName, current.toString()});
        }
        return headers;
    }

    private static String firstHeaderValue(List<String[]> headers, String name) {
        for (String[] header : headers) {
            if (header[0].equalsIgnoreCase(name)) {
                return header[1];
            }
        }
        return null;
    }

    private static String relaxedHeader(String name, String value) {
        String unfolded = value.replace("\r", "").replace("\n", "");
        String collapsed = unfolded.replaceAll("[ \t]+", " ").strip();
        return name.toLowerCase() + ":" + collapsed;
    }

    private static String relaxedBody(String body) {
        String normalized = body.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line.replaceAll("[ \t]+", " ").replaceAll("[ \t]+$", "")).append("\r\n");
        }
        String result = out.toString();
        while (result.endsWith("\r\n\r\n")) {
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String base64(byte[] input) {
        return Base64.getEncoder().encodeToString(input);
    }
}
