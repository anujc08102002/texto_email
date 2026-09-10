package com.texto.emailplatform.domain;

import java.util.Locale;

public final class PlatformSenderDomains {

    public static final String SUFFIX = ".texto.test";

    private PlatformSenderDomains() {
    }

    public static String hostForSlug(String slug) {
        return slug.toLowerCase(Locale.ROOT) + SUFFIX;
    }

    public static boolean isPlatformTestDomain(String domain) {
        return domain != null && DomainNormalizer.normalize(domain).endsWith(SUFFIX);
    }
}
