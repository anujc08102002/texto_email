package com.texto.emailplatform.billing.razorpay;

import com.texto.emailplatform.subscription.SubscriptionStatus;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps Razorpay subscription statuses and webhook event types to internal {@link SubscriptionStatus}
 * values. Provider status strings are stored separately and must never become the app status.
 */
public final class RazorpayStatusMapper {

    private RazorpayStatusMapper() {
    }

    public static Optional<String> fromProviderStatus(String razorpayStatus) {
        if (razorpayStatus == null || razorpayStatus.isBlank()) {
            return Optional.empty();
        }
        return switch (razorpayStatus.trim().toLowerCase(Locale.ROOT)) {
            case "created", "authenticated" -> Optional.of(SubscriptionStatus.ACTIVE);
            case "active" -> Optional.of(SubscriptionStatus.ACTIVE);
            case "pending" -> Optional.of(SubscriptionStatus.PAST_DUE);
            case "halted" -> Optional.of(SubscriptionStatus.SUSPENDED);
            case "cancelled", "completed" -> Optional.of(SubscriptionStatus.CANCELLED);
            case "paused" -> Optional.of(SubscriptionStatus.SUSPENDED);
            case "expired" -> Optional.of(SubscriptionStatus.EXPIRED);
            default -> Optional.empty();
        };
    }

    public static Optional<String> fromEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return Optional.empty();
        }
        return switch (eventType.trim().toLowerCase(Locale.ROOT)) {
            case "subscription.authenticated",
                 "subscription.activated",
                 "subscription.charged",
                 "subscription.resumed" -> Optional.of(SubscriptionStatus.ACTIVE);
            case "subscription.pending" -> Optional.of(SubscriptionStatus.PAST_DUE);
            case "subscription.halted",
                 "subscription.paused" -> Optional.of(SubscriptionStatus.SUSPENDED);
            case "subscription.cancelled",
                 "subscription.completed" -> Optional.of(SubscriptionStatus.CANCELLED);
            case "payment.failed" -> Optional.of(SubscriptionStatus.PAST_DUE);
            default -> Optional.empty();
        };
    }

    public static boolean isActivationEvent(String eventType) {
        if (eventType == null) {
            return false;
        }
        String normalized = eventType.trim().toLowerCase(Locale.ROOT);
        return "subscription.authenticated".equals(normalized)
                || "subscription.activated".equals(normalized)
                || "subscription.charged".equals(normalized);
    }

    public static boolean isPaymentFailureEvent(String eventType) {
        return eventType != null && "payment.failed".equalsIgnoreCase(eventType.trim());
    }
}
