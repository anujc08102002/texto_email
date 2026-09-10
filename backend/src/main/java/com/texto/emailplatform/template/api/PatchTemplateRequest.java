package com.texto.emailplatform.template.api;

import jakarta.validation.constraints.Size;

public record PatchTemplateRequest(
        @Size(max = 255) String name,
        @Size(max = 4000) String description
) {
}
