package com.texto.emailplatform.delivery;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Signs composed MIME with rsa-sha256 DKIM before MTA submission.
 *
 * <p>This repository does not yet persist DKIM private keys (Phase 8A custody is not on
 * this branch). A stable in-process key per From-domain is used so every outbound message
 * carries a real {@code DKIM-Signature}. DNS publication of that key remains a later step.
 */
@Component
public class DkimSigningService {

    public static final String SELECTOR = "texto";

    private final ConcurrentHashMap<String, KeyPair> keys = new ConcurrentHashMap<>();

    public SignedMime sign(OutboundMimeComposer.ComposedMessage composed, Instant now) {
        String domain = OutboundMimeComposer.domainOf(composed.mailFrom());
        KeyPair keyPair = keys.computeIfAbsent(domain, ignored -> generate());
        byte[] signed = DkimSigner.sign(composed.rfc822(), domain, SELECTOR, keyPair.getPrivate(), now);
        return new SignedMime(signed, keyPair.getPrivate(), keyPair.getPublic());
    }

    public PrivateKey privateKey(String domain) {
        return keys.computeIfAbsent(domain, ignored -> generate()).getPrivate();
    }

    public java.security.PublicKey publicKey(String domain) {
        return keys.computeIfAbsent(domain, ignored -> generate()).getPublic();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate DKIM signing key", exception);
        }
    }

    public record SignedMime(byte[] rfc822, PrivateKey privateKey, java.security.PublicKey publicKey) {
    }
}
