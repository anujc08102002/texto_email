package com.texto.emailplatform.domain.dkim;

import java.security.PrivateKey;

/**
 * Signing-only view of an active DKIM key. {@link #toString()} never includes key material.
 */
public final class DkimSigningMaterial {

    private final String selector;
    private final String publicKeyPkcs1;
    private final PrivateKey privateKey;

    public DkimSigningMaterial(String selector, String publicKeyPkcs1, PrivateKey privateKey) {
        this.selector = selector;
        this.publicKeyPkcs1 = publicKeyPkcs1;
        this.privateKey = privateKey;
    }

    public String selector() {
        return selector;
    }

    public String publicKeyPkcs1() {
        return publicKeyPkcs1;
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    @Override
    public String toString() {
        return "DkimSigningMaterial[selector=" + selector + "]";
    }
}
