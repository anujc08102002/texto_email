package com.texto.emailplatform.billing.spi;

import java.time.Instant;
import java.util.Map;

public record ProviderSubscription(
        String providerSubscriptionId,
        String providerCustomerId,
        String providerPlanId,
        String providerStatus,
        Instant currentStart,
        Instant currentEnd,
        Map<String, String> notes
) {
}
