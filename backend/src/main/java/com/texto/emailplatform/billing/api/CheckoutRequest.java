package com.texto.emailplatform.billing.api;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank String planCode
) {
}
