package com.texto.emailplatform.billing.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin Razorpay REST client (no official SDK). Never logs secrets or request bodies that may
 * contain them.
 */
@Component
@ConditionalOnProperty(prefix = "email-platform.billing", name = "provider", havingValue = "razorpay")
public class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);

    private final RazorpayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RazorpayClient(EmailPlatformProperties platformProperties, ObjectMapper objectMapper) {
        this(platformProperties.getBilling().getRazorpay(), objectMapper, defaultRestClient());
    }

    RazorpayClient(RazorpayProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient defaultRestClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    public JsonNode createCustomer(String name, String email, Map<String, String> notes) {
        ObjectNode body = objectMapper.createObjectNode();
        if (name != null && !name.isBlank()) {
            body.put("name", name);
        }
        if (email != null && !email.isBlank()) {
            body.put("email", email);
        }
        if (notes != null && !notes.isEmpty()) {
            body.set("notes", objectMapper.valueToTree(notes));
        }
        return post("/customers", body);
    }

    public JsonNode createSubscription(
            String planId,
            String customerId,
            Integer totalCount,
            Map<String, String> notes
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("plan_id", planId);
        if (customerId != null && !customerId.isBlank()) {
            body.put("customer_id", customerId);
        }
        body.put("total_count", totalCount != null && totalCount > 0 ? totalCount : 12);
        body.put("customer_notify", 1);
        if (notes != null && !notes.isEmpty()) {
            body.set("notes", objectMapper.valueToTree(notes));
        }
        return post("/subscriptions", body);
    }

    public JsonNode fetchSubscription(String subscriptionId) {
        return get("/subscriptions/" + subscriptionId);
    }

    public JsonNode updateSubscription(String subscriptionId, String planId, Map<String, String> notes) {
        ObjectNode body = objectMapper.createObjectNode();
        if (planId != null && !planId.isBlank()) {
            body.put("plan_id", planId);
        }
        if (notes != null && !notes.isEmpty()) {
            body.set("notes", objectMapper.valueToTree(notes));
        }
        return patch("/subscriptions/" + subscriptionId, body);
    }

    public JsonNode cancelSubscription(String subscriptionId, boolean cancelAtCycleEnd) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("cancel_at_cycle_end", cancelAtCycleEnd ? 1 : 0);
        return post("/subscriptions/" + subscriptionId + "/cancel", body);
    }

    public JsonNode pauseSubscription(String subscriptionId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("pause_at", "now");
        return post("/subscriptions/" + subscriptionId + "/pause", body);
    }

    public JsonNode resumeSubscription(String subscriptionId) {
        return post("/subscriptions/" + subscriptionId + "/resume", objectMapper.createObjectNode());
    }

    private JsonNode get(String path) {
        try {
            String response = client()
                    .get()
                    .uri(url(path))
                    .retrieve()
                    .body(String.class);
            return readTree(response);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        }
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            String response = client()
                    .post()
                    .uri(url(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readTree(response);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        }
    }

    private JsonNode patch(String path, ObjectNode body) {
        try {
            String response = client()
                    .method(HttpMethod.PATCH)
                    .uri(url(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readTree(response);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        }
    }

    private RestClient client() {
        if (!properties.hasApiCredentials()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "BILLING_NOT_CONFIGURED",
                    "Razorpay API credentials are not configured"
            );
        }
        String token = Base64.getEncoder().encodeToString(
                (properties.getKeyId() + ":" + properties.getKeySecret()).getBytes(StandardCharsets.UTF_8)
        );
        return restClient.mutate()
                .defaultHeader("Authorization", "Basic " + token)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String url(String path) {
        String base = properties.getApiBaseUrl();
        if (base == null || base.isBlank()) {
            base = "https://api.razorpay.com/v1";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private JsonNode readTree(String response) {
        try {
            if (response == null || response.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            log.warn("Failed to parse Razorpay response");
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY.value(),
                    "BILLING_PROVIDER_ERROR",
                    "Billing provider returned an unreadable response"
            );
        }
    }

    private ApiException translate(RestClientResponseException exception) {
        log.warn("Razorpay API call failed with status {}", exception.getStatusCode().value());
        String message = "Billing provider request failed";
        try {
            JsonNode error = objectMapper.readTree(exception.getResponseBodyAsString()).path("error");
            String description = text(error, "description");
            if (description != null && !description.isBlank()) {
                message = description;
            }
        } catch (Exception ignored) {
            // keep generic message — never echo secrets
        }
        if (exception.getStatusCode().value() >= 500) {
            return new ApiException(HttpStatus.BAD_GATEWAY.value(), "BILLING_PROVIDER_ERROR", message);
        }
        return new ApiException(HttpStatus.BAD_REQUEST.value(), "BILLING_PROVIDER_ERROR", message);
    }

    static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    static Map<String, String> notesMap(JsonNode node) {
        Map<String, String> notes = new LinkedHashMap<>();
        JsonNode notesNode = node == null ? null : node.get("notes");
        if (notesNode == null || !notesNode.isObject()) {
            return notes;
        }
        notesNode.fields().forEachRemaining(entry -> {
            if (entry.getValue() != null && !entry.getValue().isNull()) {
                notes.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return notes;
    }
}
