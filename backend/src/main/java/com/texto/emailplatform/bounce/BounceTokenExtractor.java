package com.texto.emailplatform.bounce;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Extracts a validated opaque bounce token from an envelope/DSN address.
 * Does not perform database lookup or establish tenant context.
 */
@Component
public class BounceTokenExtractor {

    private static final int MAX_ADDRESS_LENGTH = 320;
    private static final Pattern ANGLE = Pattern.compile("^<(.*)>$");
    private static final Pattern BOUNCE_LOCAL = Pattern.compile(
            "^bounce\\+([0-9a-fA-F]{" + BounceCorrelationToken.HEX_LENGTH + "})$"
    );

    private final EmailPlatformProperties properties;

    public BounceTokenExtractor(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    public Optional<String> extract(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return Optional.empty();
        }
        String trimmed = rawAddress.trim();
        if (trimmed.length() > MAX_ADDRESS_LENGTH) {
            return Optional.empty();
        }
        Matcher angled = ANGLE.matcher(trimmed);
        if (angled.matches()) {
            trimmed = angled.group(1).trim();
        }
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return extractRawToken(trimmed);
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
        String expected = BounceAddress.normalizeDomain(properties.getBounce().getDomain());
        if (expected == null || !expected.equals(domain)) {
            return Optional.empty();
        }
        Matcher localMatch = BOUNCE_LOCAL.matcher(local);
        if (!localMatch.matches()) {
            return Optional.empty();
        }
        return Optional.of(localMatch.group(1).toLowerCase(Locale.ROOT));
    }

    public Optional<String> extractFirst(String... candidates) {
        if (candidates == null) {
            return Optional.empty();
        }
        for (String candidate : candidates) {
            Optional<String> token = extract(candidate);
            if (token.isPresent()) {
                return token;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> extractRawToken(String value) {
        return Optional.ofNullable(BounceCorrelationToken.normalize(value));
    }
}
