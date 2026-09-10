package com.texto.emailplatform.suppression.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ImportSuppressionsRequest(
        @NotEmpty List<String> emails
) {
}
