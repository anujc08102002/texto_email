package com.texto.emailplatform.billing.api;

import jakarta.validation.constraints.NotNull;

public record CancelBillingRequest(
        @NotNull Boolean atPeriodEnd
) {
}
