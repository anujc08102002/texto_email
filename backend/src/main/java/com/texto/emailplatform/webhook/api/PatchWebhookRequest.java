package com.texto.emailplatform.webhook.api;

import jakarta.validation.constraints.Size;
import java.util.List;

public record PatchWebhookRequest(
        @Size(max = 2048) String url,
        @Size(max = 512) String description,
        List<String> eventTypes
) {
}
