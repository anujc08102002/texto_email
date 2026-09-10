package com.texto.emailplatform.auth.security;

import java.util.UUID;

public record DashboardPrincipal(UUID userId, UUID tenantId, String email, String role) {
}
