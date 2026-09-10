package com.texto.emailplatform.domain.dkim;

import java.security.PrivateKey;
import java.util.UUID;

/**
 * Internal signing-key handle returned only to the DKIM signing boundary.
 *
 * <p>This type is intentionally not a JPA entity, not a DTO, and must never be serialized, returned
 * from a controller, cached, or logged. It carries a live {@link PrivateKey} solely so the future
 * signer can produce a {@code DKIM-Signature}.
 */
public record DkimSigningKey(UUID domainId, String selector, String algorithm, PrivateKey privateKey) {

    @Override
    public String toString() {
        // Never leak key material via logging/toString.
        return "DkimSigningKey{domainId=" + domainId + ", selector=" + selector + ", algorithm=" + algorithm + "}";
    }
}
