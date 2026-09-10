package com.texto.emailplatform.billing.api;

import java.util.UUID;

public record CheckoutResponse(
        String keyId,
        String provider,
        UUID subscriptionId,
        String providerSubscriptionId,
        String planCode,
        String status
) {
}
