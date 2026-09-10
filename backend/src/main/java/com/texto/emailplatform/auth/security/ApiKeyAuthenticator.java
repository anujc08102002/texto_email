package com.texto.emailplatform.auth.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Looks up a hashed API key and returns the owning tenant principal.
 * Implementations must never accept plaintext comparisons against stored secrets.
 */
public interface ApiKeyAuthenticator {

    Optional<ApiKeyPrincipal> authenticate(String rawApiKey);
}
