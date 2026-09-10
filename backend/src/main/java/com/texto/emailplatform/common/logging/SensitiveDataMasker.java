package com.texto.emailplatform.common.logging;

import java.util.Locale;
import java.util.Set;

public final class SensitiveDataMasker {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "passwordhash",
            "secret",
            "apisecret",
            "apikey",
            "authorization",
            "token",
            "accesstoken",
            "refreshtoken",
            "paymentsecret",
            "cardnumber"
    );

    private SensitiveDataMasker() {
    }

    public static String mask(String key, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (SENSITIVE_KEYS.contains(normalized) || normalized.contains("password") || normalized.contains("secret")
                || normalized.contains("token") || normalized.contains("apikey")) {
            return maskValue(value);
        }
        return value;
    }

    public static String maskValue(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "****";
    }
}
