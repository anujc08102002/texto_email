package com.texto.emailplatform.complaint;

import java.util.Locale;

/**
 * Provider-neutral complaint category. Unknown still suppresses when correlated.
 */
public enum ComplaintType {
    ABUSE,
    SPAM,
    FRAUD,
    UNKNOWN;

    public static ComplaintType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return ComplaintType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
