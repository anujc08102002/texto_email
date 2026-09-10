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
        Instant eventCreatedAt,
        JsonNode rawPayload
) {

    /**
     * Returns a copy with the given deduplication id. Used to prefer the provider's authoritative
     * event id (e.g. Razorpay's {@code X-Razorpay-Event-Id} header) over any body-derived id.
     */
    public ProviderWebhookEvent withProviderEventId(String newProviderEventId) {
        if (newProviderEventId == null || newProviderEventId.isBlank()) {
            return this;
        }
        return new ProviderWebhookEvent(
                newProviderEventId,
                type,
                providerSubscriptionId,
                providerCustomerId,
                providerPaymentId,
                providerStatus,
                periodStart,
                periodEnd,
                suggestedInternalStatus,
                activation,
                paymentFailure,
                eventCreatedAt,
                rawPayload
        );
    }
}
