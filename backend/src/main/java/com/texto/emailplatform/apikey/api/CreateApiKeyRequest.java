package com.texto.emailplatform.apikey.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 255) String name,
        @Pattern(regexp = "TEST|LIVE") String environment,
        @Size(max = 2) Set<@Pattern(regexp = "email:(send|read)") String> scopes,
        Instant expiresAt
) {
}
