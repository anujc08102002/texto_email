package com.texto.emailplatform.webhook;

public final class WebhookEventTypes {

    public static final String EMAIL_QUEUED = "email.queued";
    public static final String EMAIL_SENDING = "email.sending";
    public static final String EMAIL_DELIVERED = "email.delivered";
    public static final String EMAIL_DEFERRED = "email.deferred";
    public static final String EMAIL_BOUNCED = "email.bounced";
    public static final String EMAIL_COMPLAINT = "email.complaint";
    public static final String EMAIL_FAILED = "email.failed";
    public static final String EMAIL_SUPPRESSED = "email.suppressed";
    public static final String WEBHOOK_TEST = "webhook.test";

    private WebhookEventTypes() {
    }
}
