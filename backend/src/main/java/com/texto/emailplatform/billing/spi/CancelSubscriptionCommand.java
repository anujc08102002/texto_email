package com.texto.emailplatform.billing.spi;

public record CancelSubscriptionCommand(
        String providerSubscriptionId,
        boolean cancelAtCycleEnd
) {
}
