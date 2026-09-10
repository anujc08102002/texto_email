package com.texto.emailplatform.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookSigner {

    public String sign(String secret, long timestampEpochSeconds, String body) {
        String payload = timestampEpochSeconds + "." + body;
        return hmacSha256Hex(secret, payload);
    }

    public String signatureHeader(long timestampEpochSeconds, String signatureHex) {
        return "t=" + timestampEpochSeconds + ",v1=" + signatureHex;
    }

    public boolean verify(String secret, long timestampEpochSeconds, String body, String signatureHex) {
        String expected = sign(secret, timestampEpochSeconds, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHex.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign webhook payload", exception);
        }
    }
}
