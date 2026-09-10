package com.texto.emailplatform.webhook;

public final class WebhookQueues {

    public static final String WEBHOOK_EXCHANGE = "email.webhooks.exchange";
    public static final String DELIVERY_QUEUE = "email.webhooks.delivery";
    public static final String DLQ = "email.webhooks.dlq";
    public static final String DELIVERY_ROUTING_KEY = "email.webhooks.delivery";
    public static final String DLQ_ROUTING_KEY = "email.webhooks.dlq";

    private WebhookQueues() {
    }
}
