package com.texto.emailplatform.auth.security;

import java.util.UUID;
import java.util.Set;

public record ApiKeyPrincipal(UUID apiKeyId, UUID tenantId, String keyPrefix, String environment, Set<String> scopes) {
    public ApiKeyPrincipal {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public ApiKeyPrincipal(UUID apiKeyId, UUID tenantId, String keyPrefix) {
        this(apiKeyId, tenantId, keyPrefix, "LIVE", Set.of("email:send", "email:read"));
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
