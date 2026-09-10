package com.texto.emailplatform.common.security;

/**
 * Permission codes evaluated by {@link RbacAuthorizationManager}.
 */
public final class Permission {

    public static final String API_KEY_CREATE = "API_KEY_CREATE";
    public static final String API_KEY_READ = "API_KEY_READ";
    public static final String API_KEY_REVOKE = "API_KEY_REVOKE";

    public static final String SUBSCRIPTION_READ = "SUBSCRIPTION_READ";
    public static final String SUBSCRIPTION_MANAGE = "SUBSCRIPTION_MANAGE";

    public static final String ENTITLEMENT_READ = "ENTITLEMENT_READ";
    public static final String USAGE_READ = "USAGE_READ";

    public static final String BILLING_READ = "BILLING_READ";
    public static final String BILLING_MANAGE = "BILLING_MANAGE";

    public static final String EMAIL_READ = "EMAIL_READ";
    public static final String EMAIL_SEND = "EMAIL_SEND";

    public static final String DOMAIN_READ = "DOMAIN_READ";
    public static final String DOMAIN_MANAGE = "DOMAIN_MANAGE";

    public static final String TEMPLATE_READ = "TEMPLATE_READ";
    public static final String TEMPLATE_MANAGE = "TEMPLATE_MANAGE";

    public static final String WEBHOOK_READ = "WEBHOOK_READ";
    public static final String WEBHOOK_MANAGE = "WEBHOOK_MANAGE";

    public static final String SUPPRESSION_READ = "SUPPRESSION_READ";
    public static final String SUPPRESSION_MANAGE = "SUPPRESSION_MANAGE";

    public static final String MEMBER_READ = "MEMBER_READ";
    public static final String MEMBER_MANAGE = "MEMBER_MANAGE";

    private Permission() {
    }
}
