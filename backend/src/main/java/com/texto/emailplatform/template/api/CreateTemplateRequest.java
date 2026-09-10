package com.texto.emailplatform.template.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateTemplateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 998) String subject,
        @NotBlank @Size(max = 500_000) String htmlContent,
        @Size(max = 500_000) String textContent,
        Map<String, Object> variablesSchema
) {
}
