package com.texto.emailplatform.billing.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * Provider-normalized webhook event. Mapping from vendor event names to internal status
 * happens inside the provider implementation — callers must not switch on Razorpay/Stripe strings.
 */
public record ProviderWebhookEvent(
        String providerEventId,
        String type,
        String providerSubscriptionId,
        String providerCustomerId,
        String providerPaymentId,
        String providerStatus,
        Instant periodStart,
        Instant periodEnd,
        String suggestedInternalStatus,
        boolean activation,
        boolean paymentFailure,
        JsonNode rawPayload
) {
}
