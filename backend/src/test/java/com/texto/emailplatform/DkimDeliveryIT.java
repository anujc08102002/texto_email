package com.texto.emailplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.domain.DnsLookupService;
import com.texto.emailplatform.domain.dkim.DkimTestVerifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end DKIM signing test: the REAL {@link com.texto.emailplatform.delivery.MailpitDeliveryEngine}
 * delivers to a real Mailpit container. The raw message is fetched back from Mailpit and its
 * DKIM-Signature is independently verified against the domain's stored public key.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class DkimDeliveryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.21"))
            .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void mailpitProperties(DynamicPropertyRegistry registry) {
        registry.add("email-platform.mailpit.host", mailpit::getHost);
        registry.add("email-platform.mailpit.smtp-port", () -> mailpit.getMappedPort(1025));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeliveryEngine deliveryEngine; // real MailpitDeliveryEngine (@Primary)

    @MockitoBean
    private DnsLookupService dnsLookupService;

    private final Map<String, List<String>> txtRecords = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void stubDns() {
        txtRecords.clear();
        when(dnsLookupService.lookupTxt(anyString())).thenAnswer(invocation ->
                txtRecords.getOrDefault(invocation.getArgument(0), List.of()));
    }

    private String mailpitBase() {
        return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    }

    @Test
    void deliveredMessageForVerifiedDomainCarriesValidDkimSignature() throws Exception {
        String token = register();
        String tenantId = JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.tenantId");

        // Create + verify a custom sending domain (this also provisions the DKIM key, Phase 8A Step 1).
        String domain = "mail.dkimit.test";
        MvcResult created = mockMvc.perform(post("/api/v1/domains")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"" + domain + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String domainId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        String verificationBody = mockMvc.perform(get("/api/v1/domains/" + domainId + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> records = JsonPath.read(verificationBody, "$.data.records");
        for (Map<String, Object> record : records) {
            txtRecords.put(String.valueOf(record.get("name")), List.of(String.valueOf(record.get("value"))));
        }
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(status().isOk());

        String publicKeyBase64 = jdbcTemplate.queryForObject(
                "select public_key from dkim_keys where domain_id = ?::uuid", String.class, domainId);
        PublicKey publicKey = DkimTestVerifier.rsaPublicKeyFromBase64Spki(publicKeyBase64);

        clearMailpit();

        // Deliver through the REAL engine from the verified domain.
        DeliveryEngine.DeliveryResult result = deliveryEngine.deliver(new DeliveryEngine.DeliveryRequest(
                UUID.fromString(tenantId),
                "noreply@" + domain,
                List.of("recipient@example.org"),
                null,
                null,
                null,
                "DKIM integration subject",
                "This body is DKIM signed.\r\n",
                null
        ));
        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.SUCCESS);

        String raw = fetchLatestRawMessage();
        String dkimValue = DkimTestVerifier.dkimSignatureHeaderValue(raw);
        assertThat(dkimValue).as("DKIM-Signature header present").isNotNull();

        Map<String, String> tags = DkimTestVerifier.parseTags(dkimValue);
        assertThat(tags.get("d")).isEqualTo(domain);
        assertThat(tags.get("s")).isEqualTo("texto");
        assertThat(tags.get("a")).isEqualTo("rsa-sha256");
        assertThat(tags.get("c")).isEqualTo("relaxed/relaxed");

        // Independent cryptographic verification against the stored public key.
        assertThat(DkimTestVerifier.verifyRaw(raw, publicKey))
                .as("DKIM signature verifies against the domain's public key")
                .isTrue();
    }

    @Test
    void platformTestSenderIsDeliveredUnsigned() throws Exception {
        String token = register();
        String tenantId = JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.tenantId");
        String slug = jdbcTemplate.queryForObject(
                "select slug from tenants where id = ?::uuid", String.class, tenantId);

        clearMailpit();

        DeliveryEngine.DeliveryResult result = deliveryEngine.deliver(new DeliveryEngine.DeliveryRequest(
                UUID.fromString(tenantId),
                "noreply@" + slug + ".texto.test",
                List.of("recipient@example.org"),
                null,
                null,
                null,
                "Platform sender subject",
                "Unsigned platform body.\r\n",
                null
        ));
        assertThat(result.outcome()).isEqualTo(DeliveryEngine.Outcome.SUCCESS);

        String raw = fetchLatestRawMessage();
        // No DKIM key is configured for *.texto.test, so the message is (correctly) unsigned.
        assertThat(DkimTestVerifier.dkimSignatureHeaderValue(raw)).isNull();
    }

    // ---- Mailpit HTTP helpers -----------------------------------------------------------------

    private void clearMailpit() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(mailpitBase() + "/api/v1/messages"))
                .DELETE().build();
        http.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private String fetchLatestRawMessage() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            HttpResponse<String> list = http.send(
                    HttpRequest.newBuilder(URI.create(mailpitBase() + "/api/v1/messages")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode messages = objectMapper.readTree(list.body()).path("messages");
            if (messages.isArray() && messages.size() > 0) {
                String id = messages.get(0).path("ID").asText();
                HttpResponse<String> raw = http.send(
                        HttpRequest.newBuilder(URI.create(mailpitBase() + "/api/v1/message/" + id + "/raw")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                return raw.body();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No message arrived in Mailpit");
    }

    private String register() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organization\":\"DKIM IT Co\",\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }
}
