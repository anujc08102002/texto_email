package com.texto.emailplatform.template.api;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String status,
        UUID currentVersionId,
        Integer currentVersion,
        Instant createdAt,
        Instant updatedAt
) {
}
