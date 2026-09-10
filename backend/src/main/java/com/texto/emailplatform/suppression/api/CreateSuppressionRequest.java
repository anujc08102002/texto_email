package com.texto.emailplatform.suppression.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSuppressionRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 64) String reason
) {
}
