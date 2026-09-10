package com.texto.emailplatform.queue;

public final class EmailDeliveryQueues {

    public static final String DELIVERY_EXCHANGE = "email.delivery.exchange";
    public static final String DELIVERY_QUEUE = "email.delivery.queue";
    public static final String DELIVERY_ROUTING_KEY = "email.delivery";

    public static final String DLX = "email.delivery.dlx";
    public static final String DLQ = "email.delivery.dlq";
    public static final String DLQ_ROUTING_KEY = "email.delivery.dlq";

    public static final String RETRY_EXCHANGE = "email.delivery.retry.exchange";

    public static final String RETRY_30S_QUEUE = "email.delivery.retry.30s";
    public static final String RETRY_2M_QUEUE = "email.delivery.retry.2m";
    public static final String RETRY_10M_QUEUE = "email.delivery.retry.10m";
    public static final String RETRY_30M_QUEUE = "email.delivery.retry.30m";
    public static final String RETRY_2H_QUEUE = "email.delivery.retry.2h";

    public static final String RETRY_30S_KEY = "email.delivery.retry.30s";
    public static final String RETRY_2M_KEY = "email.delivery.retry.2m";
    public static final String RETRY_10M_KEY = "email.delivery.retry.10m";
    public static final String RETRY_30M_KEY = "email.delivery.retry.30m";
    public static final String RETRY_2H_KEY = "email.delivery.retry.2h";

    /** Backoff seconds aligned with retry queues (attempt index 1..5). */
    public static final long[] BACKOFF_SECONDS = {30, 120, 600, 1800, 7200};

    private EmailDeliveryQueues() {
    }

    public static String retryRoutingKey(int attemptNumber) {
        int index = Math.min(Math.max(attemptNumber, 1), BACKOFF_SECONDS.length) - 1;
        return switch (index) {
            case 0 -> RETRY_30S_KEY;
            case 1 -> RETRY_2M_KEY;
            case 2 -> RETRY_10M_KEY;
            case 3 -> RETRY_30M_KEY;
            default -> RETRY_2H_KEY;
        };
    }
}
