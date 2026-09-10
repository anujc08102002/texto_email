package com.texto.emailplatform.billing.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.billing.BillingProvider;
import com.texto.emailplatform.billing.spi.BillingProviders;
import com.texto.emailplatform.billing.spi.CancelSubscriptionCommand;
import com.texto.emailplatform.billing.spi.CreateSubscriptionCommand;
import com.texto.emailplatform.billing.spi.ProviderSubscription;
import com.texto.emailplatform.billing.spi.ProviderWebhookEvent;
import com.texto.emailplatform.billing.spi.UpdateSubscriptionCommand;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "email-platform.billing", name = "provider", havingValue = "razorpay")
public class RazorpayBillingProvider implements BillingProvider {

    private final RazorpayProperties properties;
    private final RazorpayClient client;
    private final ObjectMapper objectMapper;

    public RazorpayBillingProvider(
            EmailPlatformProperties platformProperties,
            RazorpayClient client,
            ObjectMapper objectMapper
    ) {
        this.properties = platformProperties.getBilling().getRazorpay();
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return BillingProviders.RAZORPAY;
    }

    @Override
    public boolean isConfigured() {
        return properties.hasApiCredentials();
    }

    @Override
    public ProviderSubscription createSubscription(CreateSubscriptionCommand cmd) {
        requireConfigured();
        Map<String, String> notes = notesWithTenant(cmd.notes(), cmd.tenantId() == null ? null : cmd.tenantId().toString());
        String customerId = null;
        JsonNode customer = client.createCustomer(cmd.customerName(), cmd.customerEmail(), notes);
        customerId = RazorpayClient.text(customer, "id");
        JsonNode subscription = client.createSubscription(
                cmd.providerPlanId(),
                customerId,
                cmd.totalCount(),
                notes
        );
        return toProviderSubscription(subscription, customerId);
    }

    @Override
    public ProviderSubscription getSubscription(String providerSubscriptionId) {
        requireConfigured();
        return toProviderSubscription(client.fetchSubscription(providerSubscriptionId), null);
    }

    @Override
    public ProviderSubscription updateSubscription(UpdateSubscriptionCommand cmd) {
        requireConfigured();
        JsonNode subscription = client.updateSubscription(
                cmd.providerSubscriptionId(),
                cmd.providerPlanId(),
                cmd.notes() == null ? Map.of() : cmd.notes()
        );
        return toProviderSubscription(subscription, null);
    }

    @Override
    public ProviderSubscription cancelSubscription(CancelSubscriptionCommand cmd) {
        requireConfigured();
        JsonNode subscription = client.cancelSubscription(cmd.providerSubscriptionId(), cmd.cancelAtCycleEnd());
        return toProviderSubscription(subscription, null);
    }

    @Override
    public ProviderSubscription pauseSubscription(String providerSubscriptionId) {
        requireConfigured();
        return toProviderSubscription(client.pauseSubscription(providerSubscriptionId), null);
    }

    @Override
    public ProviderSubscription resumeSubscription(String providerSubscriptionId) {
        requireConfigured();
        return toProviderSubscription(client.resumeSubscription(providerSubscriptionId), null);
    }

    @Override
    public boolean verifyWebhook(String rawBody, String signatureHeader) {
        if (!properties.hasWebhookSecret() || rawBody == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String expected = hmacSha256Hex(properties.getWebhookSecret(), rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public ProviderWebhookEvent parseWebhookEvent(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            // Razorpay webhook bodies carry no unique event id — the authoritative id is the
            // X-Razorpay-Event-Id header (applied by the caller). Fall back to a synthetic id so
            // dedup still works if the header is ever absent.
            String eventId = syntheticEventId(root);
            String type = RazorpayClient.text(root, "event");
            Instant eventCreatedAt = epochSeconds(root, "created_at");
            JsonNode payload = root.path("payload");
            JsonNode subscription = payload.path("subscription").path("entity");
            if (subscription.isMissingNode() || subscription.isNull()) {
                subscription = payload.path("subscription");
            }
            JsonNode payment = payload.path("payment").path("entity");
            if (payment.isMissingNode() || payment.isNull()) {
                payment = payload.path("payment");
            }
            String providerSubscriptionId = RazorpayClient.text(subscription, "id");
            String providerCustomerId = firstNonBlank(
                    RazorpayClient.text(subscription, "customer_id"),
                    RazorpayClient.text(payment, "customer_id")
            );
            String providerPaymentId = RazorpayClient.text(payment, "id");
            String providerStatus = firstNonBlank(
                    RazorpayClient.text(subscription, "status"),
                    RazorpayClient.text(payment, "status")
            );
            Instant periodStart = epochSeconds(subscription, "current_start");
            Instant periodEnd = epochSeconds(subscription, "current_end");
            String suggestedStatus = RazorpayStatusMapper.fromEventType(type)
                    .or(() -> RazorpayStatusMapper.fromProviderStatus(providerStatus))
                    .orElse(null);
            return new ProviderWebhookEvent(
                    eventId,
                    type,
                    providerSubscriptionId,
                    providerCustomerId,
                    providerPaymentId,
                    providerStatus,
                    periodStart,
                    periodEnd,
                    suggestedStatus,
                    RazorpayStatusMapper.isActivationEvent(type),
                    RazorpayStatusMapper.isPaymentFailureEvent(type),
                    eventCreatedAt,
                    root
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST.value(),
                    "BILLING_WEBHOOK_INVALID",
                    "Unable to parse billing webhook payload"
            );
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "BILLING_NOT_CONFIGURED",
                    "Razorpay is not configured"
            );
        }
    }

    private ProviderSubscription toProviderSubscription(JsonNode subscription, String fallbackCustomerId) {
        return new ProviderSubscription(
                RazorpayClient.text(subscription, "id"),
                firstNonBlank(RazorpayClient.text(subscription, "customer_id"), fallbackCustomerId),
                RazorpayClient.text(subscription, "plan_id"),
                RazorpayClient.text(subscription, "status"),
                epochSeconds(subscription, "current_start"),
                epochSeconds(subscription, "current_end"),
                RazorpayClient.notesMap(subscription)
        );
    }

    private static Map<String, String> notesWithTenant(Map<String, String> notes, String tenantId) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (notes != null) {
            merged.putAll(notes);
        }
        if (tenantId != null) {
            merged.putIfAbsent("tenant_id", tenantId);
        }
        return merged;
    }

    private static Instant epochSeconds(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isNumber()) {
            long epoch = value.asLong();
            return epoch <= 0 ? null : Instant.ofEpochSecond(epoch);
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            long epoch = Long.parseLong(text);
            return epoch <= 0 ? null : Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String syntheticEventId(JsonNode root) {
        String event = RazorpayClient.text(root, "event");
        long createdAt = root.path("created_at").asLong(0L);
        String subscriptionId = RazorpayClient.text(root.path("payload").path("subscription").path("entity"), "id");
        return "synthetic:" + event + ":" + subscriptionId + ":" + createdAt;
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to verify Razorpay webhook signature", exception);
        }
    }
}
