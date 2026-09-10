package com.texto.emailplatform.plan;

/**
 * Feature codes seeded into the {@code features} table. Plan availability is data-driven.
 */
public final class FeatureCodes {

    public static final String API_SENDING = "API_SENDING";
    public static final String SMTP_SENDING = "SMTP_SENDING";
    public static final String CUSTOM_DOMAIN = "CUSTOM_DOMAIN";
    public static final String TEMPLATES = "TEMPLATES";
    public static final String WEBHOOKS = "WEBHOOKS";
    public static final String BASIC_ANALYTICS = "BASIC_ANALYTICS";
    public static final String ADVANCED_ANALYTICS = "ADVANCED_ANALYTICS";
    public static final String SUPPRESSION = "SUPPRESSION";
    public static final String BOUNCE_HANDLING = "BOUNCE_HANDLING";
    public static final String CAMPAIGNS = "CAMPAIGNS";
    public static final String SEGMENTATION = "SEGMENTATION";
    public static final String CUSTOM_TRACKING_DOMAIN = "CUSTOM_TRACKING_DOMAIN";
    public static final String DEDICATED_IP = "DEDICATED_IP";
    public static final String IP_POOLS = "IP_POOLS";
    public static final String REPUTATION_DASHBOARD = "REPUTATION_DASHBOARD";

    private FeatureCodes() {
    }
}
