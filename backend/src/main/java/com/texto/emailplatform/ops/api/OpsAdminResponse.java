package com.texto.emailplatform.ops.api;

import java.util.UUID;

public record OpsAdminResponse(UUID id, String email, String name, String role) {
}
