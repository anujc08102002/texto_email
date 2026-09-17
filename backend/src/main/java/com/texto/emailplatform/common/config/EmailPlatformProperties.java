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
    private final Ses ses = new Ses();
    private final Domains domains = new Domains();
    private final Webhooks webhooks = new Webhooks();
    private final Email email = new Email();
    private final Bounce bounce = new Bounce();
    private final Complaint complaint = new Complaint();
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

    public Ses getSes() {
        return ses;
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

    public Bounce getBounce() {
        return bounce;
    }

    public Complaint getComplaint() {
        return complaint;
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
        /**
         * When true, customer SPF TXT includes {@code ip4:<MTA_OUTBOUND_IP>} and omits the unprovisioned include.
         */
        private boolean spfAuthorizeOutboundIp = false;
        /**
         * When true, {@code SPF_INCLUDE} is an actually published DNS include. Default false:
         * {@code _spf.texto.email} is not provisioned yet.
         */
        private boolean spfIncludeProvisioned = false;
        /** Selector used for newly generated DKIM keys. Existing records keep their stored selector. */
        private String dkimSelector = "texto";
        /**
         * Base64-encoded 32-byte AES-256 key for DKIM private-key custody.
         * Required in production ({@code DKIM_KEY_ENCRYPTION_KEY}).
         */
        private String dkimKeyEncryptionKey;
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

        public boolean isSpfAuthorizeOutboundIp() {
            return spfAuthorizeOutboundIp;
        }

        public void setSpfAuthorizeOutboundIp(boolean spfAuthorizeOutboundIp) {
            this.spfAuthorizeOutboundIp = spfAuthorizeOutboundIp;
        }

        public boolean isSpfIncludeProvisioned() {
            return spfIncludeProvisioned;
        }

        public void setSpfIncludeProvisioned(boolean spfIncludeProvisioned) {
            this.spfIncludeProvisioned = spfIncludeProvisioned;
        }

        public String getDkimSelector() {
            return dkimSelector;
        }

        public void setDkimSelector(String dkimSelector) {
            this.dkimSelector = dkimSelector;
        }

        public String getDkimKeyEncryptionKey() {
            return dkimKeyEncryptionKey;
        }

        public void setDkimKeyEncryptionKey(String dkimKeyEncryptionKey) {
            this.dkimKeyEncryptionKey = dkimKeyEncryptionKey;
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
        /** Must stay ≤ Postfix message_size_limit (10 MiB). */
        private int maxRfc822Bytes = 10_485_760;

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

        public int getMaxRfc822Bytes() {
            return maxRfc822Bytes;
        }

        public void setMaxRfc822Bytes(int maxRfc822Bytes) {
            this.maxRfc822Bytes = maxRfc822Bytes;
        }
    }

    /**
     * Inbound DSN resource limits. Untrusted input; not an SMTP listener.
     */
    public static class Bounce {
        private String domain = "bounce.texto.test";
        /** Optional public inbound MX owner name for readiness diagnostics only. */
        private String mxHostname = "";
        private String spoolDirectory = "";
        private int maxRfc822Bytes = 262_144;
        private int maxMimeParts = 20;
        private int maxMimeDepth = 8;
        private int maxDiagnosticLength = 512;
        private int maxHeaderLength = 255;
        private int maxRecipients = 50;

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getMxHostname() {
            return mxHostname;
        }

        public void setMxHostname(String mxHostname) {
            this.mxHostname = mxHostname;
        }

        public String getSpoolDirectory() {
            return spoolDirectory;
        }

        public void setSpoolDirectory(String spoolDirectory) {
            this.spoolDirectory = spoolDirectory;
        }

        public int getMaxRfc822Bytes() {
            return maxRfc822Bytes;
        }

        public void setMaxRfc822Bytes(int maxRfc822Bytes) {
            this.maxRfc822Bytes = maxRfc822Bytes;
        }

        public int getMaxMimeParts() {
            return maxMimeParts;
        }

        public void setMaxMimeParts(int maxMimeParts) {
            this.maxMimeParts = maxMimeParts;
        }

        public int getMaxMimeDepth() {
            return maxMimeDepth;
        }

        public void setMaxMimeDepth(int maxMimeDepth) {
            this.maxMimeDepth = maxMimeDepth;
        }

        public int getMaxDiagnosticLength() {
            return maxDiagnosticLength;
        }

        public void setMaxDiagnosticLength(int maxDiagnosticLength) {
            this.maxDiagnosticLength = maxDiagnosticLength;
        }

        public int getMaxHeaderLength() {
            return maxHeaderLength;
        }

        public void setMaxHeaderLength(int maxHeaderLength) {
            this.maxHeaderLength = maxHeaderLength;
        }

        public int getMaxRecipients() {
            return maxRecipients;
        }

        public void setMaxRecipients(int maxRecipients) {
            this.maxRecipients = maxRecipients;
        }
    }

    /**
     * Complaint / feedback-loop resource limits. Untrusted input; not a public FBL listener.
     */
    public static class Complaint {
        private int maxPayloadBytes = 65_536;
        private int maxProviderLength = 64;
        private int maxProviderEventIdLength = 128;
        private int maxMessageIdLength = 255;
        private int maxRecipientLength = 320;
        private int maxCorrelationTokenLength = 320;
        private int maxDiagnosticLength = 512;
        private int maxMetadataEntries = 16;
        private int maxMetadataValueLength = 256;

        public int getMaxPayloadBytes() {
            return maxPayloadBytes;
        }

        public void setMaxPayloadBytes(int maxPayloadBytes) {
            this.maxPayloadBytes = maxPayloadBytes;
        }

        public int getMaxProviderLength() {
            return maxProviderLength;
        }

        public void setMaxProviderLength(int maxProviderLength) {
            this.maxProviderLength = maxProviderLength;
        }

        public int getMaxProviderEventIdLength() {
            return maxProviderEventIdLength;
        }

        public void setMaxProviderEventIdLength(int maxProviderEventIdLength) {
            this.maxProviderEventIdLength = maxProviderEventIdLength;
        }

        public int getMaxMessageIdLength() {
            return maxMessageIdLength;
        }

        public void setMaxMessageIdLength(int maxMessageIdLength) {
            this.maxMessageIdLength = maxMessageIdLength;
        }

        public int getMaxRecipientLength() {
            return maxRecipientLength;
        }

        public void setMaxRecipientLength(int maxRecipientLength) {
            this.maxRecipientLength = maxRecipientLength;
        }

        public int getMaxCorrelationTokenLength() {
            return maxCorrelationTokenLength;
        }

        public void setMaxCorrelationTokenLength(int maxCorrelationTokenLength) {
            this.maxCorrelationTokenLength = maxCorrelationTokenLength;
        }

        public int getMaxDiagnosticLength() {
            return maxDiagnosticLength;
        }

        public void setMaxDiagnosticLength(int maxDiagnosticLength) {
            this.maxDiagnosticLength = maxDiagnosticLength;
        }

        public int getMaxMetadataEntries() {
            return maxMetadataEntries;
        }

        public void setMaxMetadataEntries(int maxMetadataEntries) {
            this.maxMetadataEntries = maxMetadataEntries;
        }

        public int getMaxMetadataValueLength() {
            return maxMetadataValueLength;
        }

        public void setMaxMetadataValueLength(int maxMetadataValueLength) {
            this.maxMetadataValueLength = maxMetadataValueLength;
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
        /**
         * Application kill switch for Internet MX submission. Default false.
         * Production Postfix submits to recipient MX only when this is true
         * <em>and</em> the Postfix image has public delivery confirmed.
         */
        private boolean publicDeliveryEnabled = false;
        /** SMTP identity FQDN (e.g. smtp.example.com). Not assumed to exist in DNS. */
        private String hostname;
        /** Documented outbound IPv4. Not used by the Java SMTP client. */
        private String outboundIp;
        @NestedConfigurationProperty
        private final Smtp smtp = new Smtp();

        public String getImplementation() {
            return implementation;
        }

        public void setImplementation(String implementation) {
            this.implementation = implementation;
        }

        public boolean isPublicDeliveryEnabled() {
            return publicDeliveryEnabled;
        }

        public void setPublicDeliveryEnabled(boolean publicDeliveryEnabled) {
            this.publicDeliveryEnabled = publicDeliveryEnabled;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public String getOutboundIp() {
            return outboundIp;
        }

        public void setOutboundIp(String outboundIp) {
            this.outboundIp = outboundIp;
        }

        public Smtp getSmtp() {
            return smtp;
        }
    }

    public static class Ses {
        private boolean enabled;
        private String region = "ap-south-1";
        /**
         * Optional SES Configuration Set name. This is not the SNS topic/destination name.
         */
        private String configurationSetName;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getConfigurationSetName() {
            return configurationSetName;
        }

        public void setConfigurationSetName(String configurationSetName) {
            this.configurationSetName = configurationSetName;
        }
    }

    public static class Smtp {
        private String host = "localhost";
        private int port = 1025;
        private int connectionTimeoutMs = 10_000;
        private int readTimeoutMs = 30_000;
        private int writeTimeoutMs = 30_000;
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
