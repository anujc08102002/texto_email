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
        String body = """
                {
                  "id": "evt_abc",
                  "event": "subscription.activated",
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

        assertThat(event.providerEventId()).isEqualTo("evt_abc");
        assertThat(event.type()).isEqualTo("subscription.activated");
        assertThat(event.providerSubscriptionId()).isEqualTo("sub_123");
        assertThat(event.providerCustomerId()).isEqualTo("cust_9");
        assertThat(event.providerStatus()).isEqualTo("active");
        assertThat(event.periodStart()).isNotNull();
        assertThat(event.periodEnd()).isNotNull();
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
