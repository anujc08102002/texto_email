package com.texto.emailplatform.usage.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Usage snapshot for the current calendar-month billing period.
 * A {@code null} limit or remaining value means unlimited.
 */
public record UsageResponse(
        UUID tenantId,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<MetricUsage> metrics
) {

    public record MetricUsage(String metric, long used, Long limit, Long remaining) {
    }
}
