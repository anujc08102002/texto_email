package com.texto.emailplatform.auth;

import java.util.Locale;

public final class TenantSlugger {

    private TenantSlugger() {
    }

    public static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (slug.isBlank()) {
            slug = "tenant";
        }
        if (slug.length() > 48) {
            slug = slug.substring(0, 48).replaceAll("-+$", "");
            if (slug.isBlank()) {
                slug = "tenant";
            }
        }
        return slug;
    }
}
