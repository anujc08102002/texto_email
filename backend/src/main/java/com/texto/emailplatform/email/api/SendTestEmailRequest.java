package com.texto.emailplatform.email.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendTestEmailRequest(
        @NotBlank @Email @Size(max = 320) String to,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 20000) String body
) {
}
