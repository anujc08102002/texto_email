package com.texto.emailplatform.ops.security;

import java.util.UUID;

public record PlatformAdminPrincipal(UUID adminId, String email, String displayName) {
}
