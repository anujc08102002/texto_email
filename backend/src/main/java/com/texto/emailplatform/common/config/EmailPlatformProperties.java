package com.texto.emailplatform.common.config;

import com.texto.emailplatform.billing.razorpay.RazorpayProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "email-platform")
public class EmailPlatformProperties {

    private final Cors cors = new Cors();
    private final Security security = new Security();
    private final Mailpit mailpit = new Mailpit();
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
        /**
         * SPF include domain published to customers. Sending IPs are published behind this name later.
         */
        private String spfInclude = "_spf.texto.email";
        /** RFC 7208 qualifier for {@code all}: {@code +}, {@code -}, {@code ?}, or {@code ~}. */
        private String spfAllQualifier = "~";
        /** Selector used for newly generated DKIM keys. Existing records keep their stored selector. */
        private String dkimSelector = "texto";
        private String dmarcPolicy = "none";
        private String dmarcSubdomainPolicy;
        private String dmarcRua;
        private String dmarcRuf;
        private String dmarcDkimAlignment;
        private String dmarcSpfAlignment;
        private Integer dmarcPercentage;
        private int verifyMaxPerMinute = 10;
        private int dnsTimeoutMs = 2000;

        public boolean isAllowPlatformTestSenders() {
            return allowPlatformTestSenders;
        }

        public void setAllowPlatformTestSenders(boolean allowPlatformTestSenders) {
            this.allowPlatformTestSenders = allowPlatformTestSenders;
        }

        public String getSpfInclude() {
            return spfInclude;
        }

        public void setSpfInclude(String spfInclude) {
            this.spfInclude = spfInclude;
        }

        public String getSpfAllQualifier() {
            return spfAllQualifier;
        }

        public void setSpfAllQualifier(String spfAllQualifier) {
            this.spfAllQualifier = spfAllQualifier;
        }

        public String getDkimSelector() {
            return dkimSelector;
        }

        public void setDkimSelector(String dkimSelector) {
            this.dkimSelector = dkimSelector;
        }

        public String getDmarcPolicy() {
            return dmarcPolicy;
        }

        public void setDmarcPolicy(String dmarcPolicy) {
            this.dmarcPolicy = dmarcPolicy;
        }

        public String getDmarcSubdomainPolicy() {
            return dmarcSubdomainPolicy;
        }

        public void setDmarcSubdomainPolicy(String dmarcSubdomainPolicy) {
            this.dmarcSubdomainPolicy = dmarcSubdomainPolicy;
        }

        public String getDmarcRua() {
            return dmarcRua;
        }

        public void setDmarcRua(String dmarcRua) {
            this.dmarcRua = dmarcRua;
        }

        public String getDmarcRuf() {
            return dmarcRuf;
        }

        public void setDmarcRuf(String dmarcRuf) {
            this.dmarcRuf = dmarcRuf;
        }

        public String getDmarcDkimAlignment() {
            return dmarcDkimAlignment;
        }

        public void setDmarcDkimAlignment(String dmarcDkimAlignment) {
            this.dmarcDkimAlignment = dmarcDkimAlignment;
        }

        public String getDmarcSpfAlignment() {
            return dmarcSpfAlignment;
        }

        public void setDmarcSpfAlignment(String dmarcSpfAlignment) {
            this.dmarcSpfAlignment = dmarcSpfAlignment;
        }

        public Integer getDmarcPercentage() {
            return dmarcPercentage;
        }

        public void setDmarcPercentage(Integer dmarcPercentage) {
            this.dmarcPercentage = dmarcPercentage;
        }

        public int getVerifyMaxPerMinute() {
            return verifyMaxPerMinute;
        }

        public void setVerifyMaxPerMinute(int verifyMaxPerMinute) {
            this.verifyMaxPerMinute = verifyMaxPerMinute;
        }

        public int getDnsTimeoutMs() {
            return dnsTimeoutMs;
        }

        public void setDnsTimeoutMs(int dnsTimeoutMs) {
            this.dnsTimeoutMs = dnsTimeoutMs;
        }

        public com.texto.emailplatform.domain.dns.DmarcSettings dmarcSettings() {
            return new com.texto.emailplatform.domain.dns.DmarcSettings(
                    dmarcPolicy,
                    dmarcSubdomainPolicy,
                    dmarcRua,
                    dmarcRuf,
                    dmarcDkimAlignment,
                    dmarcSpfAlignment,
                    dmarcPercentage
            );
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

    public static class Mta {
        private String implementation = "mailpit";
        @NestedConfigurationProperty
        private final Smtp smtp = new Smtp();

        public String getImplementation() {
            return implementation;
        }

        public void setImplementation(String implementation) {
            this.implementation = implementation;
        }

        public Smtp getSmtp() {
            return smtp;
        }
    }

    public static class Smtp {
        private String host = "localhost";
        private int port = 1025;
        private int connectionTimeoutMs = 5_000;
        private int readTimeoutMs = 5_000;
        private int writeTimeoutMs = 5_000;
        private String ehloHostname = "texto.local";
        @NestedConfigurationProperty
        private final StartTls starttls = new StartTls();
        @NestedConfigurationProperty
        private final Ssl ssl = new Ssl();

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

        public String getEhloHostname() {
            return ehloHostname;
        }

        public void setEhloHostname(String ehloHostname) {
            this.ehloHostname = ehloHostname;
        }

        public StartTls getStarttls() {
            return starttls;
        }

        public Ssl getSsl() {
            return ssl;
        }
    }

    public static class StartTls {
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
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
