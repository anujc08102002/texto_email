package com.texto.emailplatform.usage;

import java.util.List;

/**
 * Metric codes shared by {@code plan_limits} and {@code tenant_usage}.
 */
public final class UsageMetrics {

    public static final String MONTHLY_EMAILS = "MONTHLY_EMAILS";
    public static final String DOMAINS = "DOMAINS";
    public static final String USERS = "USERS";
    public static final String API_KEYS = "API_KEYS";
    public static final String TEMPLATES = "TEMPLATES";
    public static final String CAMPAIGNS = "CAMPAIGNS";

    public static final List<String> ALL = List.of(
            MONTHLY_EMAILS,
            DOMAINS,
            USERS,
            API_KEYS,
            TEMPLATES,
            CAMPAIGNS
    );

    private UsageMetrics() {
    }
}
