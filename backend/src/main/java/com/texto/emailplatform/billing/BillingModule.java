package com.texto.emailplatform.billing;

/**
 * Billing module: provider-agnostic checkout and webhooks. App DB remains the source of truth
 * for entitlements; Razorpay (TEST) is the first {@link BillingProvider} implementation.
 */
public final class BillingModule {

    private BillingModule() {
    }
}
