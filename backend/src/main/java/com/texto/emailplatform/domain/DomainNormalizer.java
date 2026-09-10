package com.texto.emailplatform.domain;

import java.util.Locale;

public final class DomainNormalizer {

    private DomainNormalizer() {
    }

    public static String normalize(String host) {
        if (host == null) {
            return "";
        }
        String value = host.trim().toLowerCase(Locale.ROOT);
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
