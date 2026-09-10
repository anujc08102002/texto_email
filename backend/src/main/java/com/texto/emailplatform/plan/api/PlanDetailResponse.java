package com.texto.emailplatform.plan.api;

import java.util.Map;
import java.util.UUID;

/**
 * Plan catalog entry including the feature matrix and limits loaded from the database.
 * A {@code null} limit value means unlimited.
 */
public record PlanDetailResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        int sortOrder,
        Map<String, Boolean> features,
        Map<String, Long> limits
) {
}
