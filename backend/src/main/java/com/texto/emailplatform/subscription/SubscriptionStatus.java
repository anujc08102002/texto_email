package com.texto.emailplatform.subscription;

import java.util.Set;

/**
 * Subscription lifecycle states persisted as plain strings.
 */
public final class SubscriptionStatus {

    public static final String TRIAL = "TRIAL";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAST_DUE = "PAST_DUE";
    public static final String GRACE_PERIOD = "GRACE_PERIOD";
    public static final String RESTRICTED = "RESTRICTED";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    /**
     * States where plan features remain fully usable.
     */
    public static final Set<String> ENTITLED = Set.of(TRIAL, ACTIVE, PAST_DUE, GRACE_PERIOD);

    /**
     * States where nothing is entitled any more.
     */
    public static final Set<String> BLOCKED = Set.of(SUSPENDED, CANCELLED, EXPIRED);

    private SubscriptionStatus() {
    }
}
