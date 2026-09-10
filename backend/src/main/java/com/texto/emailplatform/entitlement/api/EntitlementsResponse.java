package com.texto.emailplatform.entitlement.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Effective entitlements for a tenant. A {@code null} limit value means unlimited.
 */
public record EntitlementsResponse(
        UUID tenantId,
        String planCode,
        String planName,
        String subscriptionStatus,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Map<String, Boolean> features,
        Map<String, Long> limits,
        Map<String, Long> usage
) {
}
