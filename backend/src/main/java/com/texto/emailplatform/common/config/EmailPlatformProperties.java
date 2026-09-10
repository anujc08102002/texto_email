package com.texto.emailplatform.common.config;

import com.texto.emailplatform.billing.razorpay.RazorpayProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "email-platform")
public class EmailPlatformProperties {

    private final Cors cors = new Cors();
    private final Security security = new Security();
    private final Mailpit mailpit = new Mailpit();
    private final Domains domains = new Domains();
    private final Webhooks webhooks = new Webhooks();
    private final Email email = new Email();
    private final Billing billing = new Billing();

    public Cors getCors() {
        return cors;
    }

    public Security getSecurity() {
        return security;
    }

    public Mailpit getMailpit() {
        return mailpit;
    }

    public Domains getDomains() {
        return domains;
    }

    public Webhooks getWebhooks() {
        return webhooks;
    }

    public Email getEmail() {
        return email;
    }

    public Billing getBilling() {
        return billing;
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:3000";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Security {
        private boolean apiDocsPublic;

        public boolean isApiDocsPublic() {
            return apiDocsPublic;
        }

        public void setApiDocsPublic(boolean apiDocsPublic) {
            this.apiDocsPublic = apiDocsPublic;
        }
    }

    public static class Mailpit {
        private String host = "localhost";
        private int smtpPort = 1025;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getSmtpPort() {
            return smtpPort;
        }

        public void setSmtpPort(int smtpPort) {
            this.smtpPort = smtpPort;
        }
    }

    public static class Domains {
        /**
         * When true, allow from-addresses like {@code user@{tenantSlug}.texto.test} without a verified custom domain.
         */
        private boolean allowPlatformTestSenders = true;

        public boolean isAllowPlatformTestSenders() {
            return allowPlatformTestSenders;
        }

        public void setAllowPlatformTestSenders(boolean allowPlatformTestSenders) {
            this.allowPlatformTestSenders = allowPlatformTestSenders;
        }
    }

    public static class Webhooks {
        private boolean allowHttp;
        private int maxAttempts = 5;
        private int connectTimeoutMs = 2_000;
        private int readTimeoutMs = 5_000;
        /** Base64-encoded 32-byte AES key. Required for multi-instance production. */
        private String encryptionKey;

        public boolean isAllowHttp() {
            return allowHttp;
        }

        public void setAllowHttp(boolean allowHttp) {
            this.allowHttp = allowHttp;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public String getEncryptionKey() {
            return encryptionKey;
        }

        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }
    }

    public static class Email {
        private int maxRecipients = 50;
        private int maxAttempts = 5;
        private long outboxPollMs = 500;
        private int rateLimitPerMinute = 120;

        public int getMaxRecipients() {
            return maxRecipients;
        }

        public void setMaxRecipients(int maxRecipients) {
            this.maxRecipients = maxRecipients;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getOutboxPollMs() {
            return outboxPollMs;
        }

        public void setOutboxPollMs(long outboxPollMs) {
            this.outboxPollMs = outboxPollMs;
        }

        public int getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public void setRateLimitPerMinute(int rateLimitPerMinute) {
            this.rateLimitPerMinute = rateLimitPerMinute;
        }
    }

    public static class Billing {
        private String provider = "noop";
        private int gracePeriodDays = 3;

        @NestedConfigurationProperty
        private final RazorpayProperties razorpay = new RazorpayProperties();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public int getGracePeriodDays() {
            return gracePeriodDays;
        }

        public void setGracePeriodDays(int gracePeriodDays) {
            this.gracePeriodDays = gracePeriodDays;
        }

        public RazorpayProperties getRazorpay() {
            return razorpay;
        }
    }
}
