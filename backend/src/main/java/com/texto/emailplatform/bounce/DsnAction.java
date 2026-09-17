package com.texto.emailplatform.bounce;

/**
 * RFC 3464 {@code Action} values. Unknown tokens are preserved as {@link #UNKNOWN}.
 */
public enum DsnAction {
    FAILED,
    DELAYED,
    DELIVERED,
    RELAYED,
    EXPANDED,
    UNKNOWN;

    public static DsnAction fromField(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return switch (value.trim().toLowerCase()) {
            case "failed" -> FAILED;
            case "delayed" -> DELAYED;
            case "delivered" -> DELIVERED;
            case "relayed" -> RELAYED;
            case "expanded" -> EXPANDED;
            default -> UNKNOWN;
        };
    }
}
