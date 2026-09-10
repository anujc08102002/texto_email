package com.texto.emailplatform.domain.dkim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure cryptographic DKIM signer implementing RFC 6376 with the {@code relaxed/relaxed}
 * canonicalization and the {@code rsa-sha256} algorithm.
 *
 * <p>This component performs no I/O, no database access, and knows nothing about tenants, HTTP,
 * RabbitMQ, Mailpit, or billing. It is given the finalized message, a private key, the (verified)
 * signing domain, and the active selector, and returns the value for a {@code DKIM-Signature}
 * header. Domain/key resolution and verified-sender enforcement live outside this component.
 *
 * <p><b>Canonicalization choice:</b> {@code relaxed/relaxed}. Relaxed header/body canonicalization
 * tolerates the whitespace and folding normalization that is common for transactional email in
 * transit, so signatures survive benign reformatting far better than {@code simple}.
 *
 * <p><b>Signed headers ({@code h=}):</b> {@link #SIGNED_HEADERS} — {@code From} (required by RFC),
 * {@code To}, {@code Cc}, {@code Subject}, {@code Date}, {@code Message-ID}, {@code MIME-Version},
 * {@code Content-Type}, {@code Reply-To} — each included only when present in the message. The
 * {@code DKIM-Signature} header itself is never listed.
 */
@Component
public class DkimSigner {

    /** Application signed-header preference order. Only headers present in the message are used. */
    public static final List<String> SIGNED_HEADERS = List.of(
            "From", "To", "Cc", "Subject", "Date", "Message-ID", "MIME-Version", "Content-Type", "Reply-To"
    );

    private static final String CRLF = "\r\n";

    /**
     * Signs the message and returns the full {@code DKIM-Signature} header value (i.e. everything
     * after {@code "DKIM-Signature: "}), folded across lines.
     */
    public String sign(DkimSignableMessage message, PrivateKey privateKey, String signingDomain, String selector) {
        if (privateKey == null) {
            throw new IllegalArgumentException("private key is required for DKIM signing");
        }
        if (signingDomain == null || signingDomain.isBlank()) {
            throw new IllegalArgumentException("signing domain is required for DKIM signing");
        }
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("selector is required for DKIM signing");
        }

        // 1-4. Canonicalize the body and compute bh=.
        String bodyHash = Base64.getEncoder().encodeToString(
                sha256(relaxedBody(message.body()).getBytes(StandardCharsets.UTF_8))
        );

        // 5. Determine the signed header set (present headers, in preference order) for h=.
        List<DkimSignableMessage.Header> signedHeaders = new ArrayList<>();
        List<String> hTagNames = new ArrayList<>();
        for (String name : SIGNED_HEADERS) {
            List<DkimSignableMessage.Header> present = message.headersNamed(name);
            if (!present.isEmpty()) {
                // Single instance per header in our messages; include the first occurrence.
                signedHeaders.add(present.get(0));
                hTagNames.add(present.get(0).name());
            }
        }
        if (hTagNames.stream().noneMatch(n -> n.equalsIgnoreCase("From"))) {
            throw new IllegalArgumentException("DKIM requires the From header to be present and signed");
        }
        String hTag = String.join(":", hTagNames);

        long timestamp = Instant.now().getEpochSecond();

        // 6. Build the DKIM-Signature tag string with an empty b=. The SAME tag order/values are
        //    reused for transmission (only b= is filled), so the verifier reproduces this exactly.
        String tagsWithEmptyB = "v=1; a=rsa-sha256; c=relaxed/relaxed;"
                + " d=" + signingDomain + "; s=" + selector + "; t=" + timestamp + ";"
                + " h=" + hTag + ";"
                + " bh=" + bodyHash + ";"
                + " b=";

        // 7. Build the signing input: canonicalized signed headers (each + CRLF), then the
        //    canonicalized DKIM-Signature (empty b=) WITHOUT a trailing CRLF.
        StringBuilder signingInput = new StringBuilder();
        for (DkimSignableMessage.Header header : signedHeaders) {
            signingInput.append(relaxedHeader(header.name(), header.value())).append(CRLF);
        }
        signingInput.append(relaxedHeader("DKIM-Signature", tagsWithEmptyB));

        // 8. Sign with RSA-SHA256.
        String signature = Base64.getEncoder().encodeToString(
                rsaSha256(privateKey, signingInput.toString().getBytes(StandardCharsets.US_ASCII))
        );

        // 9. Emit the transmitted header value: identical tags/order, b= filled, folded as FWS only
        //    at tag boundaries (safe under relaxed canonicalization) and inside the b= base64.
        return "v=1; a=rsa-sha256; c=relaxed/relaxed;" + CRLF
                + "\td=" + signingDomain + "; s=" + selector + "; t=" + timestamp + ";" + CRLF
                + "\th=" + hTag + ";" + CRLF
                + "\tbh=" + bodyHash + ";" + CRLF
                + "\tb=" + foldBase64(signature);
    }

    // ---- RFC 6376 relaxed canonicalization ----------------------------------------------------

    /** Relaxed header canonicalization (RFC 6376 §3.4.2). */
    static String relaxedHeader(String name, String value) {
        String unfolded = value == null ? "" : value.replace("\r", "").replace("\n", "");
        // Collapse WSP runs to a single SP, then trim leading/trailing WSP around the value.
        String collapsed = unfolded.replaceAll("[ \t]+", " ").trim();
        return name.toLowerCase() + ":" + collapsed;
    }

    /** Relaxed body canonicalization (RFC 6376 §3.4.4). */
    static String relaxedBody(String body) {
        String normalized = (body == null ? "" : body).replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            // Reduce WSP runs to a single SP and strip trailing WSP.
            String canonicalLine = line.replaceAll("[ \t]+", " ").replaceAll("[ \t]+$", "");
            out.append(canonicalLine).append(CRLF);
        }
        // Ignore all empty lines at the end of the body (reduce trailing CRLFs to a single one).
        String result = out.toString();
        while (result.endsWith(CRLF + CRLF)) {
            result = result.substring(0, result.length() - CRLF.length());
        }
        return result;
    }

    private static String foldBase64(String base64) {
        int chunk = 72;
        StringBuilder folded = new StringBuilder();
        for (int i = 0; i < base64.length(); i += chunk) {
            if (i > 0) {
                folded.append(CRLF).append('\t');
            }
            folded.append(base64, i, Math.min(i + chunk, base64.length()));
        }
        return folded.toString();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] rsaSha256(PrivateKey privateKey, byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute DKIM RSA-SHA256 signature", exception);
        }
    }
}
