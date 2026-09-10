package com.texto.emailplatform.billing.api;

public record BillingConfigResponse(
        String provider,
        boolean configured,
        String keyId
) {
}
