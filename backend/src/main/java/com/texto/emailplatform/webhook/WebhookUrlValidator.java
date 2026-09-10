package com.texto.emailplatform.webhook;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WebhookUrlValidator {

    private final EmailPlatformProperties properties;

    public WebhookUrlValidator(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    public String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw invalid("url is required");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw invalid("url is invalid");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw invalid("url must include scheme and host");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        boolean allowHttp = properties.getWebhooks().isAllowHttp();
        if (!"https".equals(scheme) && !("http".equals(scheme) && allowHttp)) {
            throw invalid(allowHttp ? "url must use http or https" : "url must use https");
        }
        if (uri.getUserInfo() != null) {
            throw invalid("url must not include userinfo");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (isBlockedHost(host)) {
            throw invalid("url host is not allowed");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    throw invalid("url resolves to a private or link-local address");
                }
            }
        } catch (UnknownHostException exception) {
            throw invalid("url host could not be resolved");
        }
        return uri.toString();
    }

    static boolean isBlockedHost(String host) {
        return "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "0.0.0.0".equals(host)
                || "169.254.169.254".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local");
    }

    static boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isMetadata(address);
    }

    private static boolean isMetadata(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return (bytes[0] & 0xff) == 169 && (bytes[1] & 0xff) == 254;
        }
        return false;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL", message);
    }
}
