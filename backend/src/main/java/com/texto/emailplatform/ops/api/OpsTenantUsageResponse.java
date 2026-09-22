package com.texto.emailplatform.ops.api;

import com.texto.emailplatform.entitlement.api.EntitlementsResponse;
import com.texto.emailplatform.usage.api.UsageResponse;
import java.time.Instant;
import java.util.UUID;

public record OpsTenantUsageResponse(
        UUID tenantId,
        UUID subscriptionId,
        String planCode,
        String planName,
        String subscriptionStatus,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        EntitlementsResponse entitlements,
        UsageResponse usage
) {
}
