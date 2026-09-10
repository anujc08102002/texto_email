package com.texto.emailplatform.template.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TemplateVersionResponse(
        UUID id,
        UUID templateId,
        int version,
        String subject,
        String htmlContent,
        String textContent,
        Map<String, Object> variablesSchema,
        Instant createdAt
) {
}
