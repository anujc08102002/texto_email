package com.texto.emailplatform.queue;

public final class EmailQueues {

    public static final String EMAIL_EXCHANGE = "email.exchange";
    public static final String OUTBOUND_QUEUE = "email.outbound";
    public static final String BOUNCE_QUEUE = "email.bounce";
    public static final String OUTBOUND_ROUTING_KEY = "email.outbound";
    public static final String BOUNCE_ROUTING_KEY = "email.bounce";

    private EmailQueues() {
    }
}
