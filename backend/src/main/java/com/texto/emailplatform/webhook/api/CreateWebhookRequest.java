package com.texto.emailplatform.webhook.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateWebhookRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 512) String description,
        @NotEmpty List<@NotBlank String> eventTypes
) {
}
