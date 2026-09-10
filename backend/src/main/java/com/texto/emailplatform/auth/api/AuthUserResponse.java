package com.texto.emailplatform.auth.api;

import java.util.UUID;

public record AuthUserResponse(UUID userId, UUID tenantId, String email, String role, String organization) {
}
