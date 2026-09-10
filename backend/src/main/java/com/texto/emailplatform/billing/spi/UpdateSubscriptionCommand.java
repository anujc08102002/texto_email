package com.texto.emailplatform.billing.spi;

import java.util.Map;

public record UpdateSubscriptionCommand(
        String providerSubscriptionId,
        String providerPlanId,
        Map<String, String> notes
) {
}
