package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Reports whether the configured SMTP MTA accepts a connection and returns RFC 5321 220.
 * Does not send mail. UP means "MTA reachable", not "recipient MX delivered" and not
 * "public Internet delivery is ready".
 */
@Component
@ConditionalOnProperty(name = "management.health.mta.enabled", havingValue = "true", matchIfMissing = true)
public class MtaHealthIndicator implements HealthIndicator {

    private final EmailPlatformProperties properties;
    private final PublicDeliveryGuard publicDeliveryGuard;
    private final ProductionReadinessAssessor readinessAssessor;
    private final Environment environment;

    @Autowired
    public MtaHealthIndicator(
            EmailPlatformProperties properties,
            PublicDeliveryGuard publicDeliveryGuard,
            ProductionReadinessAssessor readinessAssessor,
            Environment environment
    ) {
        this.properties = properties;
        this.publicDeliveryGuard = publicDeliveryGuard;
        this.readinessAssessor = readinessAssessor;
        this.environment = environment;
    }

    public MtaHealthIndicator(EmailPlatformProperties properties) {
        this(properties, null, new ProductionReadinessAssessor(properties, null, (RedisConnectionFactory) null), null);
    }

    @Override
    public Health health() {
        var smtp = properties.getMta().getSmtp();
        String implementation = MtaClients.normalize(properties.getMta().getImplementation());
        String host = smtp.getHost();
        int port = smtp.getPort();
        int timeout = Math.max(250, Math.min(smtp.getConnectionTimeoutMs(), 5_000));
        boolean publicEnabled = properties.getMta().isPublicDeliveryEnabled();
        boolean allowSubmit = publicDeliveryGuard == null || publicDeliveryGuard.allowMtaSubmit();
        String identity = MtaPropertiesValidator.smtpIdentity(properties);
        String bounceDomain = properties.getBounce().getDomain();
        boolean dkimKeyConfigured = properties.getDomains().getDkimKeyEncryptionKey() != null
                && !properties.getDomains().getDkimKeyEncryptionKey().isBlank();

        if (MtaClients.SES.equals(implementation)) {
            boolean configured = properties.getSes().isEnabled()
                    && properties.getSes().getRegion() != null
                    && !properties.getSes().getRegion().isBlank();
            Health.Builder builder = configured ? Health.up() : Health.down();
            return builder
                    .withDetail("implementation", implementation)
                    .withDetail("provider", "sesv2")
                    .withDetail("region", properties.getSes().getRegion())
                    .withDetail("configurationSetName", blankToEmpty(properties.getSes().getConfigurationSetName()))
                    .withDetail("reachable", "not_checked")
                    .withDetail("dkimEncryptionConfigured", dkimKeyConfigured)
                    .withDetail("publicDeliveryEnabled", publicEnabled)
                    .withDetail("applicationSubmitAllowed", allowSubmit)
                    .withDetail("internetDelivery", internetDeliveryStatus(publicEnabled, allowSubmit))
                    .withDetail("recipientDelivery", "not_checked")
                    .build();
        }

        Health.Builder builder;
        boolean reachable = false;
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);
            var in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
            var out = new java.io.BufferedWriter(
                    new java.io.OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.US_ASCII));
            String greeting = in.readLine();
            if (greeting == null || !greeting.startsWith("220")) {
                builder = Health.down()
                        .withDetail("reachable", false)
                        .withDetail("greeting", greeting == null ? "empty" : greeting);
            } else {
                try {
                    out.write("QUIT\r\n");
                    out.flush();
                } catch (Exception ignored) {
                    // Reachability already confirmed.
                }
                reachable = true;
                builder = Health.up().withDetail("reachable", true);
            }
        } catch (Exception exception) {
            builder = Health.down(exception).withDetail("reachable", false);
        }

        boolean production = environment != null && environment.acceptsProfiles("prod");
        ProductionReadinessReport readiness = readinessAssessor.assess(reachable, production);

        Health.Builder reported = builder
                .withDetail("implementation", implementation)
                .withDetail("host", host)
                .withDetail("port", port)
                .withDetail("hostname", identity.isBlank() ? null : identity)
                .withDetail("bounceDomain", bounceDomain)
                .withDetail("dkimEncryptionConfigured", dkimKeyConfigured)
                .withDetail("publicDeliveryEnabled", publicEnabled)
                .withDetail("applicationSubmitAllowed", allowSubmit)
                .withDetail("internetDelivery", internetDeliveryStatus(publicEnabled, allowSubmit))
                .withDetail("recipientDelivery", "not_checked");
        for (var entry : readiness.healthDetails().entrySet()) {
            reported.withDetail(entry.getKey(), entry.getValue());
        }
        return reported.build();
    }

    public static String internetDeliveryStatus(boolean publicEnabled, boolean allowSubmit) {
        if (!publicEnabled || !allowSubmit) {
            return "disabled";
        }
        return "not_verified";
    }

    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
