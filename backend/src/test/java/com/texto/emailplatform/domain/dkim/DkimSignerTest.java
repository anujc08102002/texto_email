package com.texto.emailplatform.domain.dkim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link DkimSigner} against an INDEPENDENT verifier implemented from scratch in this test
 * (separate relaxed-canonicalization code + JCA {@code SHA256withRSA}). The mutation tests prove the
 * signature is cryptographically bound to the signed headers and body. (A cross-implementation check
 * with Python {@code dkimpy} is performed in the Mailpit manual-validation step.)
 */
class DkimSignerTest {

    private final DkimSigner signer = new DkimSigner();

    private KeyPair keyPair;
    private KeyPair otherKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    private static DkimSignableMessage sampleMessage(String from, String subject, String body) {
        List<DkimSignableMessage.Header> headers = new ArrayList<>();
        headers.add(new DkimSignableMessage.Header("From", from));
        headers.add(new DkimSignableMessage.Header("To", "recipient@example.org"));
        headers.add(new DkimSignableMessage.Header("Subject", subject));
        headers.add(new DkimSignableMessage.Header("Date", "Thu, 10 Sep 2026 08:42:00 +0000"));
        headers.add(new DkimSignableMessage.Header("Message-ID", "<abc123@example.com>"));
        headers.add(new DkimSignableMessage.Header("MIME-Version", "1.0"));
        headers.add(new DkimSignableMessage.Header("Content-Type", "text/plain; charset=UTF-8"));
        return new DkimSignableMessage(headers, body);
    }

    @Test
    void producesValidSignatureWithExpectedTags() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Hello there", "This is the body.\r\n");

        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");
        Map<String, String> tags = parseTags(headerValue);

        assertThat(tags.get("v")).isEqualTo("1");
        assertThat(tags.get("a")).isEqualTo("rsa-sha256");
        assertThat(tags.get("c")).isEqualTo("relaxed/relaxed");
        assertThat(tags.get("d")).isEqualTo("example.com");
        assertThat(tags.get("s")).isEqualTo("texto");
        assertThat(tags.get("bh")).isNotBlank();
        assertThat(tags.get("b")).isNotBlank();
        assertThat(tags.get("h")).contains("From").contains("Subject");
        // From must be signed (RFC 6376 requirement).
        assertThat(tags.get("h").toLowerCase()).contains("from");

        assertThat(verify(message, headerValue, keyPair.getPublic())).isTrue();
    }

    @Test
    void publicKeyVerifiesSignature() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Subject A", "Body A\r\n");
        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");
        assertThat(verify(message, headerValue, keyPair.getPublic())).isTrue();
    }

    @Test
    void wrongPublicKeyFailsVerification() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Subject A", "Body A\r\n");
        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");
        assertThat(verify(message, headerValue, otherKeyPair.getPublic())).isFalse();
    }

    @Test
    void modifiedBodyFailsVerification() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Subject A", "Original body\r\n");
        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");

        DkimSignableMessage tampered = sampleMessage("hello@example.com", "Subject A", "TAMPERED body\r\n");
        assertThat(verify(tampered, headerValue, keyPair.getPublic())).isFalse();
    }

    @Test
    void modifiedFromFailsVerification() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Subject A", "Body\r\n");
        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");

        DkimSignableMessage tampered = sampleMessage("attacker@example.com", "Subject A", "Body\r\n");
        assertThat(verify(tampered, headerValue, keyPair.getPublic())).isFalse();
    }

    @Test
    void modifiedSubjectFailsVerification() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Original subject", "Body\r\n");
        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");

        DkimSignableMessage tampered = sampleMessage("hello@example.com", "Tampered subject", "Body\r\n");
        assertThat(verify(tampered, headerValue, keyPair.getPublic())).isFalse();
    }

    @Test
    void modifiedSignedHeaderFailsVerification() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Subject", "Body\r\n");
        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");

        // Change the To header (which is in h=) after signing.
        List<DkimSignableMessage.Header> headers = new ArrayList<>(message.headers());
        headers.replaceAll(h -> h.name().equals("To")
                ? new DkimSignableMessage.Header("To", "someone-else@example.org") : h);
        DkimSignableMessage tampered = new DkimSignableMessage(headers, message.body());
        assertThat(verify(tampered, headerValue, keyPair.getPublic())).isFalse();
    }

    @Test
    void modifiedSignatureFailsVerification() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Subject", "Body\r\n");
        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");

        // Rebuild the header with a corrupted b= value (flip the first base64 char).
        String b = parseTags(headerValue).get("b");
        char flipped = b.charAt(0) == 'A' ? 'B' : 'A';
        int bIdx = headerValue.lastIndexOf("b=");
        String tamperedHeader = headerValue.substring(0, bIdx + 2) + flipped + b.substring(1);
        assertThat(verify(message, tamperedHeader, keyPair.getPublic())).isFalse();
    }

    @Test
    void trailingEmptyBodyLinesAreCanonicalizedAway() {
        DkimSignableMessage base = sampleMessage("hello@example.com", "Subject", "Line one\r\n");
        DkimSignableMessage withTrailing = sampleMessage("hello@example.com", "Subject", "Line one\r\n\r\n\r\n");

        // Both bodies must produce the same bh (relaxed body ignores trailing empty lines).
        String h1 = signer.sign(base, keyPair.getPrivate(), "example.com", "texto");
        String h2 = signer.sign(withTrailing, keyPair.getPrivate(), "example.com", "texto");
        assertThat(parseTags(h1).get("bh")).isEqualTo(parseTags(h2).get("bh"));

        // And a signature made over the base body still verifies against the trailing-lines body.
        assertThat(verify(withTrailing, h1, keyPair.getPublic())).isTrue();
    }

    @Test
    void internalWhitespaceIsCanonicalizedInBody() {
        DkimSignableMessage spaced = sampleMessage("hello@example.com", "Subject", "a   b\t c \r\n");
        DkimSignableMessage single = sampleMessage("hello@example.com", "Subject", "a b c\r\n");
        assertThat(parseTags(signer.sign(spaced, keyPair.getPrivate(), "example.com", "texto")).get("bh"))
                .isEqualTo(parseTags(signer.sign(single, keyPair.getPrivate(), "example.com", "texto")).get("bh"));
    }

    @Test
    void longSignatureHeaderIsFoldedButStillVerifies() {
        DkimSignableMessage message = sampleMessage("hello@example.com", "Subject", "Body\r\n");
        String headerValue = signer.sign(message, keyPair.getPrivate(), "example.com", "texto");

        // The header is folded (contains CRLF + whitespace) yet still verifies.
        assertThat(headerValue).contains("\r\n\t");
        assertThat(verify(message, headerValue, keyPair.getPublic())).isTrue();
    }

    // ================= Independent verifier (separate implementation) =========================

    private static boolean verify(DkimSignableMessage message, String dkimHeaderValue, PublicKey publicKey) {
        Map<String, String> tags = parseTags(dkimHeaderValue);
        // 1. Body hash check.
        String expectedBh = base64(sha256(relaxedBodyIndependent(message.body()).getBytes(StandardCharsets.UTF_8)));
        if (!expectedBh.equals(tags.get("bh"))) {
            return false;
        }
        // 2. Rebuild the signing input from h= over the message headers.
        StringBuilder signingInput = new StringBuilder();
        for (String name : tags.get("h").split(":")) {
            String value = firstHeader(message, name.trim());
            if (value == null) {
                return false;
            }
            signingInput.append(relaxedHeaderIndependent(name.trim(), value)).append("\r\n");
        }
        // DKIM-Signature with an empty b= (everything up to and including "b=").
        int bIdx = dkimHeaderValue.lastIndexOf("b=");
        String noB = dkimHeaderValue.substring(0, bIdx + 2);
        signingInput.append(relaxedHeaderIndependent("DKIM-Signature", noB));
        // 3. Verify RSA-SHA256.
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingInput.toString().getBytes(StandardCharsets.US_ASCII));
            byte[] sig = Base64.getDecoder().decode(tags.get("b").replaceAll("\\s", ""));
            return verifier.verify(sig);
        } catch (Exception exception) {
            return false;
        }
    }

    private static String firstHeader(DkimSignableMessage message, String name) {
        return message.headers().stream()
                .filter(h -> h.name().equalsIgnoreCase(name))
                .map(DkimSignableMessage.Header::value)
                .findFirst()
                .orElse(null);
    }

    private static Map<String, String> parseTags(String headerValue) {
        String unfolded = headerValue.replaceAll("\\r?\\n[ \\t]+", " ");
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

    // Deliberately re-implemented (not calling DkimSigner) so a shared bug cannot hide.
    private static String relaxedHeaderIndependent(String name, String value) {
        String unfolded = value.replace("\r", "").replace("\n", "");
        StringBuilder collapsed = new StringBuilder();
        boolean lastWasWsp = false;
        for (char c : unfolded.toCharArray()) {
            if (c == ' ' || c == '\t') {
                if (!lastWasWsp) {
                    collapsed.append(' ');
                }
                lastWasWsp = true;
            } else {
                collapsed.append(c);
                lastWasWsp = false;
            }
        }
        return name.toLowerCase() + ":" + collapsed.toString().strip();
    }

    private static String relaxedBodyIndependent(String body) {
        String normalized = body.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String collapsed = line.replaceAll("[ \t]+", " ");
            while (collapsed.endsWith(" ") || collapsed.endsWith("\t")) {
                collapsed = collapsed.substring(0, collapsed.length() - 1);
            }
            out.append(collapsed).append("\r\n");
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
