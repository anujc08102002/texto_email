package com.texto.emailplatform.subscription.api;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        UUID planId,
        String planCode,
        String planName,
        String status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant trialStart,
        Instant trialEnd,
        boolean cancelAtPeriodEnd,
        Instant cancelledAt,
        Instant createdAt,
        String provider,
        String providerSubscriptionId,
        String pendingPlanCode,
        Instant gracePeriodEndsAt
) {
}
