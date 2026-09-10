package com.texto.emailplatform.domain.dns;

import java.util.Locale;

/**
 * Domain-level DMARC settings. Platform defaults live in configuration; additional
 * tags can be set later without changing the verification engine.
 */
public record DmarcSettings(
        String policy,
        String subdomainPolicy,
        String rua,
        String ruf,
        String dkimAlignment,
        String spfAlignment,
        Integer percentage
) {

    public static DmarcSettings defaults() {
        return new DmarcSettings("none", null, null, null, null, null, null);
    }

    public String normalizedPolicy() {
        if (policy == null || policy.isBlank()) {
            return "none";
        }
        return policy.trim().toLowerCase(Locale.ROOT);
    }
}
