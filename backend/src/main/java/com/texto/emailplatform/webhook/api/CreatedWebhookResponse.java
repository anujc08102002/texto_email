package com.texto.emailplatform.webhook.api;

public record CreatedWebhookResponse(WebhookConfigResponse webhook, String secret) {
}
