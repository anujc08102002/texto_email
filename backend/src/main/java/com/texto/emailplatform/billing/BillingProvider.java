package com.texto.emailplatform.billing;

import com.texto.emailplatform.billing.spi.CancelSubscriptionCommand;
import com.texto.emailplatform.billing.spi.CreateSubscriptionCommand;
import com.texto.emailplatform.billing.spi.ProviderSubscription;
import com.texto.emailplatform.billing.spi.ProviderWebhookEvent;
import com.texto.emailplatform.billing.spi.UpdateSubscriptionCommand;

/**
 * Payment provider abstraction. Application DB remains the source of truth for entitlements;
 * providers only create/update payment instruments and emit lifecycle events.
 */
public interface BillingProvider {

    String name();

    boolean isConfigured();

    ProviderSubscription createSubscription(CreateSubscriptionCommand cmd);

    ProviderSubscription getSubscription(String providerSubscriptionId);

    ProviderSubscription updateSubscription(UpdateSubscriptionCommand cmd);

    ProviderSubscription cancelSubscription(CancelSubscriptionCommand cmd);

    ProviderSubscription pauseSubscription(String providerSubscriptionId);

    ProviderSubscription resumeSubscription(String providerSubscriptionId);

    boolean verifyWebhook(String rawBody, String signatureHeader);

    ProviderWebhookEvent parseWebhookEvent(String rawBody);
}
