package com.texto.emailplatform.queue;

public final class EmailQueues {

    public static final String EMAIL_EXCHANGE = "email.exchange";
    public static final String OUTBOUND_QUEUE = "email.outbound";
    public static final String OUTBOUND_ROUTING_KEY = "email.outbound";

    public static final String BOUNCE_QUEUE = "email.bounce";
    public static final String BOUNCE_ROUTING_KEY = "email.bounce";
    public static final String BOUNCE_DLX = "email.bounce.dlx";
    public static final String BOUNCE_DLQ = "email.bounce.dlq";
    public static final String BOUNCE_DLQ_ROUTING_KEY = "email.bounce.dlq";
    public static final String ENVELOPE_RECIPIENT_HEADER = "x-envelope-recipient";

    public static final String COMPLAINT_QUEUE = "email.complaint";
    public static final String COMPLAINT_ROUTING_KEY = "email.complaint";
    public static final String COMPLAINT_DLX = "email.complaint.dlx";
    public static final String COMPLAINT_DLQ = "email.complaint.dlq";
    public static final String COMPLAINT_DLQ_ROUTING_KEY = "email.complaint.dlq";

    private EmailQueues() {
    }
}
