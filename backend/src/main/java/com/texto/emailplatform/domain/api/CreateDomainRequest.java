package com.texto.emailplatform.domain.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDomainRequest(
        @NotBlank @Size(max = 255) String domain
) {
}
