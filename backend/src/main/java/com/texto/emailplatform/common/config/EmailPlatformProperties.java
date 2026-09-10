package com.texto.emailplatform.common.config;

import com.texto.emailplatform.billing.razorpay.RazorpayProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "email-platform")
public class EmailPlatformProperties {

    private final Cors cors = new Cors();
    private final Security security = new Security();
    private final Mailpit mailpit = new Mailpit();
    @NestedConfigurationProperty
    private final Mta mta = new Mta();
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

    public Mta getMta() {
        return mta;
    }

    public String resolvedMtaHost() {
        String host = mta.getSmtp().getHost();
        if (host == null || host.isBlank()) {
            return mailpit.getHost();
        }
        return host.trim();
    }

    public int resolvedMtaPort() {
        int port = mta.getSmtp().getPort();
        if (port <= 0) {
            return mailpit.getSmtpPort();
        }
        return port;
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

    /**
     * Outbound MTA transport. Local default is Mailpit without TLS.
     * Production Postfix is not implemented in this step.
     */
    public static class Mta {
        /** {@code mailpit} is the only supported implementation. */
        private String implementation = "mailpit";
        private boolean requireDkim = true;
        private String ehloHost = "texto.local";
        @NestedConfigurationProperty
        private final Smtp smtp = new Smtp();
        @NestedConfigurationProperty
        private final StartTls starttls = new StartTls();
        @NestedConfigurationProperty
        private final Ssl ssl = new Ssl();

        public String getImplementation() {
            return implementation;
        }

        public void setImplementation(String implementation) {
            this.implementation = implementation;
        }

        public boolean isRequireDkim() {
            return requireDkim;
        }

        public void setRequireDkim(boolean requireDkim) {
            this.requireDkim = requireDkim;
        }

        public String getEhloHost() {
            return ehloHost;
        }

        public void setEhloHost(String ehloHost) {
            this.ehloHost = ehloHost;
        }

        public Smtp getSmtp() {
            return smtp;
        }

        public StartTls getStarttls() {
            return starttls;
        }

        public Ssl getSsl() {
            return ssl;
        }
    }

    public static class Smtp {
        /** When empty, {@link Mailpit#getHost()} is used. */
        private String host = "";
        /** When 0, {@link Mailpit#getSmtpPort()} is used. */
        private int port;
        private int connectionTimeoutMs = 5_000;
        private int readTimeoutMs = 5_000;
        private int writeTimeoutMs = 5_000;
        private String username = "";
        private String password = "";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getWriteTimeoutMs() {
            return writeTimeoutMs;
        }

        public void setWriteTimeoutMs(int writeTimeoutMs) {
            this.writeTimeoutMs = writeTimeoutMs;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class StartTls {
        /** Local Mailpit: disabled. Future application-to-Postfix hop: enable and typically require. */
        private boolean enabled;
        private boolean required;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }

    public static class Ssl {
        /** Implicit TLS (SMTPS, typically port 465). Off for Mailpit. */
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
