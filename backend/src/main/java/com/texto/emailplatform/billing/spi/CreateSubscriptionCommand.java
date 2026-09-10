package com.texto.emailplatform.billing.spi;

import java.util.Map;
import java.util.UUID;

public record CreateSubscriptionCommand(
        UUID tenantId,
        String planCode,
        String providerPlanId,
        String customerEmail,
        String customerName,
        Integer totalCount,
        Map<String, String> notes
) {
}
