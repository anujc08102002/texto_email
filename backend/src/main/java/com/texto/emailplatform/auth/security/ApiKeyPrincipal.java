package com.texto.emailplatform.auth.security;

import java.util.UUID;

public record ApiKeyPrincipal(UUID apiKeyId, UUID tenantId, String keyPrefix) {
}
