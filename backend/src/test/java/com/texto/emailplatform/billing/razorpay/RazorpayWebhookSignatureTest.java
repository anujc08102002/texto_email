package com.texto.emailplatform.billing.razorpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RazorpayWebhookSignatureTest {

    private RazorpayBillingProvider provider;

    @BeforeEach
    void setUp() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getBilling().setProvider("razorpay");
        properties.getBilling().getRazorpay().setKeyId("rzp_test_key");
        properties.getBilling().getRazorpay().setKeySecret("secret");
        properties.getBilling().getRazorpay().setWebhookSecret("whsec_test");
        // Real client is unused for signature/parse tests — avoid Mockito inline agent on constrained JVMs.
        RazorpayClient unusedClient = new RazorpayClient(properties, new ObjectMapper());
        provider = new RazorpayBillingProvider(properties, unusedClient, new ObjectMapper());
    }

    @Test
    void verifyWebhookAcceptsValidHmacSha256HexSignature() {
        String body = "{\"event\":\"subscription.activated\",\"id\":\"evt_1\"}";
        String signature = hmac("whsec_test", body);

        assertThat(provider.verifyWebhook(body, signature)).isTrue();
    }

    @Test
    void verifyWebhookRejectsTamperedBody() {
        String signature = hmac("whsec_test", "{\"event\":\"subscription.activated\"}");

        assertThat(provider.verifyWebhook("{\"event\":\"subscription.cancelled\"}", signature)).isFalse();
    }

    @Test
    void verifyWebhookRejectsMissingSignature() {
        assertThat(provider.verifyWebhook("{}", null)).isFalse();
        assertThat(provider.verifyWebhook("{}", "")).isFalse();
    }

    @Test
    void parseWebhookEventExtractsSubscriptionFields() throws Exception {
        // Real Razorpay webhook bodies carry no top-level event id; the unique id is the
        // X-Razorpay-Event-Id header. parseWebhookEvent therefore derives a synthetic fallback id
        // from the body and reads created_at for out-of-order detection.
        String body = """
                {
                  "entity": "event",
                  "event": "subscription.activated",
                  "created_at": 1700000500,
                  "payload": {
                    "subscription": {
                      "entity": {
                        "id": "sub_123",
                        "customer_id": "cust_9",
                        "status": "active",
                        "current_start": 1700000000,
                        "current_end": 1702678400
                      }
                    }
                  }
                }
                """;

        var event = provider.parseWebhookEvent(body);

        assertThat(event.providerEventId()).startsWith("synthetic:subscription.activated:sub_123");
        assertThat(event.type()).isEqualTo("subscription.activated");
        assertThat(event.providerSubscriptionId()).isEqualTo("sub_123");
        assertThat(event.providerCustomerId()).isEqualTo("cust_9");
        assertThat(event.providerStatus()).isEqualTo("active");
        assertThat(event.periodStart()).isNotNull();
        assertThat(event.periodEnd()).isNotNull();
        assertThat(event.eventCreatedAt()).isEqualTo(java.time.Instant.ofEpochSecond(1700000500L));
    }

    @Test
    void withProviderEventIdPrefersHeaderIdForIdempotency() {
        var event = provider.parseWebhookEvent("{\"event\":\"subscription.activated\"}");
        var overridden = event.withProviderEventId("evt_header_123");

        assertThat(overridden.providerEventId()).isEqualTo("evt_header_123");
        // Blank header must not clobber the fallback id.
        assertThat(event.withProviderEventId(" ").providerEventId()).isEqualTo(event.providerEventId());
    }

    private static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
