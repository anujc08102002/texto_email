package com.texto.emailplatform.billing;

import com.texto.emailplatform.billing.spi.BillingProviders;
import com.texto.emailplatform.billing.spi.CancelSubscriptionCommand;
import com.texto.emailplatform.billing.spi.CreateSubscriptionCommand;
import com.texto.emailplatform.billing.spi.ProviderSubscription;
import com.texto.emailplatform.billing.spi.ProviderWebhookEvent;
import com.texto.emailplatform.billing.spi.UpdateSubscriptionCommand;
import com.texto.emailplatform.common.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Default provider while no gateway is wired. FREE plan needs no payment interaction;
 * paid operations fail with {@code BILLING_NOT_CONFIGURED}.
 */
@Component
@ConditionalOnProperty(
        prefix = "email-platform.billing",
        name = "provider",
        havingValue = "noop",
        matchIfMissing = true
)
public class NoOpBillingProvider implements BillingProvider {

    @Override
    public String name() {
        return BillingProviders.NOOP;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public ProviderSubscription createSubscription(CreateSubscriptionCommand cmd) {
        throw notConfigured();
    }

    @Override
    public ProviderSubscription getSubscription(String providerSubscriptionId) {
        throw notConfigured();
    }

    @Override
    public ProviderSubscription updateSubscription(UpdateSubscriptionCommand cmd) {
        throw notConfigured();
    }

    @Override
    public ProviderSubscription cancelSubscription(CancelSubscriptionCommand cmd) {
        throw notConfigured();
    }

    @Override
    public ProviderSubscription pauseSubscription(String providerSubscriptionId) {
        throw notConfigured();
    }

    @Override
    public ProviderSubscription resumeSubscription(String providerSubscriptionId) {
        throw notConfigured();
    }

    @Override
    public boolean verifyWebhook(String rawBody, String signatureHeader) {
        return false;
    }

    @Override
    public ProviderWebhookEvent parseWebhookEvent(String rawBody) {
        throw notConfigured();
    }

    private static ApiException notConfigured() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "BILLING_NOT_CONFIGURED",
                "No billing provider is configured"
        );
    }
}
