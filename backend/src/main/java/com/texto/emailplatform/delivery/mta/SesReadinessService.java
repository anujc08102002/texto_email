package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

/** Explicit read-only provider check. Never sends mail or provisions AWS resources. */
@Service
public class SesReadinessService {
    private final SesV2Client ses;
    private final EmailPlatformProperties properties;

    public SesReadinessService(SesV2Client ses, EmailPlatformProperties properties) {
        this.ses = ses; this.properties = properties;
    }

    public Readiness inspect(String identity) {
        var checks = new LinkedHashMap<String, String>();
        String config = properties.getSes().getConfigurationSetName();
        config = config == null ? "" : config.trim();
        if (!MtaClients.SES.equals(MtaClients.normalize(properties.getMta().getImplementation())) || !properties.getSes().isEnabled()) {
            checks.put("provider", "SES_NOT_ENABLED");
            return result(checks, null, null, null);
        }
        if (!config.matches("[A-Za-z0-9_-]{1,64}") || config.equalsIgnoreCase("YOUR_CONFIGURATION_SET_NAME")) {
            checks.put("configurationSet", "ACTUAL_CONFIGURATION_SET_REQUIRED");
            return result(checks, null, null, null);
        }
        if (identity == null || !identity.matches("[A-Za-z0-9@._+\\-]{1,320}")) {
            checks.put("identity", "SENDING_DOMAIN_OR_EMAIL_REQUIRED");
            return result(checks, null, null, null);
        }
        checks.put("feedbackConsumer", properties.getSesFeedback().isEnabled()
                && properties.getSesFeedback().getQueueUrl() != null && properties.getSesFeedback().getQueueUrl().startsWith("https://sqs.")
                ? "CONFIGURED_NOT_VERIFIED" : "NOT_CONFIGURED");
        try {
            var account = ses.getAccount(GetAccountRequest.builder().overrideConfiguration(o -> o
                    .apiCallTimeout(Duration.ofSeconds(5)).apiCallAttemptTimeout(Duration.ofSeconds(3))).build());
            checks.put("accountSending", Boolean.TRUE.equals(account.sendingEnabled()) ? "VERIFIED" : "DISABLED");
            checks.put("productionAccess", Boolean.TRUE.equals(account.productionAccessEnabled()) ? "VERIFIED" : "SANDBOX");
            var set = ses.getConfigurationSet(GetConfigurationSetRequest.builder().configurationSetName(config)
                    .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(5))).build());
            checks.put("configurationSet", set.sendingOptions() != null && Boolean.FALSE.equals(set.sendingOptions().sendingEnabled())
                    ? "SENDING_DISABLED" : "VERIFIED");
            var destinations = ses.getConfigurationSetEventDestinations(GetConfigurationSetEventDestinationsRequest.builder()
                    .configurationSetName(config).overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(5))).build());
            Set<EventType> events = new HashSet<>();
            destinations.eventDestinations().stream().filter(destination -> Boolean.TRUE.equals(destination.enabled()))
                    .forEach(destination -> events.addAll(destination.matchingEventTypes()));
            checks.put("bounceEvents", events.contains(EventType.BOUNCE) ? "VERIFIED" : "NOT_CONFIGURED");
            checks.put("complaintEvents", events.contains(EventType.COMPLAINT) ? "VERIFIED" : "NOT_CONFIGURED");
            var sender = ses.getEmailIdentity(GetEmailIdentityRequest.builder().emailIdentity(identity)
                    .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(5))).build());
            checks.put("sendingIdentity", Boolean.TRUE.equals(sender.verifiedForSendingStatus()) ? "VERIFIED" : "NOT_VERIFIED");
            checks.put("identityDkim", sender.dkimAttributes() != null && Boolean.TRUE.equals(sender.dkimAttributes().signingEnabled())
                    && sender.dkimAttributes().status() == DkimStatus.SUCCESS ? "VERIFIED" : "NOT_VERIFIED");
            var quota = account.sendQuota();
            return result(checks, quota == null ? null : quota.max24HourSend(), quota == null ? null : quota.sentLast24Hours(), quota == null ? null : quota.maxSendRate());
        } catch (SdkClientException exception) {
            checks.put("providerAccess", "CREDENTIALS_OR_CONNECTION_UNAVAILABLE");
        } catch (SesV2Exception exception) {
            checks.put("providerAccess", exception.statusCode() == 403 ? "ACCESS_DENIED" : exception.statusCode() == 404 ? "RESOURCE_NOT_FOUND" : "PROVIDER_CHECK_FAILED");
        }
        return result(checks, null, null, null);
    }

    private Readiness result(Map<String, String> checks, Double max24Hours, Double sent24Hours, Double perSecond) {
        boolean verified = checks.size() >= 8 && checks.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("feedbackConsumer")).allMatch(entry -> entry.getValue().equals("VERIFIED"));
        return new Readiness(verified ? "PROVIDER_CHECKS_PASSED" : "ACTION_REQUIRED", properties.getSes().getRegion(),
                properties.getSes().getConfigurationSetName(), checks, max24Hours, sent24Hours, perSecond, Instant.now(),
                "Read-only provider checks. SQS/SNS policy, feedback consumption and received-message verification remain required.");
    }

    public record Readiness(String status, String region, String configurationSetName, Map<String, String> checks,
            Double max24HourSend, Double sentLast24Hours, Double maxSendRate, Instant checkedAt, String note) {}
}
