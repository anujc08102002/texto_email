package com.texto.emailplatform.apikey.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 255) String name,
        @Pattern(regexp = "TEST|LIVE") String environment,
        Instant expiresAt
) {
}
