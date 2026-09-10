package com.texto.emailplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.domain.DnsLookupService;
import com.texto.emailplatform.domain.dns.DnsTxtQueryResult;
import com.texto.emailplatform.email.EmailDeliveryWorker;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class Phase4CoreIT {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailDeliveryWorker emailDeliveryWorker;

    @MockitoBean
    private DeliveryEngine deliveryEngine;

    @MockitoBean
    private DnsLookupService dnsLookupService;

    private final Map<String, List<String>> txtRecords = new ConcurrentHashMap<>();

    @BeforeEach
    void stubDns() {
        txtRecords.clear();
        when(dnsLookupService.lookupTxt(anyString())).thenAnswer(invocation -> {
            List<String> values = txtRecords.get(invocation.getArgument(0));
            if (values == null || values.isEmpty()) {
                return DnsTxtQueryResult.missing();
            }
            return DnsTxtQueryResult.ok(values);
        });
        when(deliveryEngine.deliver(any())).thenReturn(
                DeliveryEngine.DeliveryResult.success("<id@texto.local>", "250 Ok")
        );
    }

    @Test
    void templatesAreTenantIsolatedAndVersionsActivate() throws Exception {
        String tenantA = register();
        String tenantB = register();

        MvcResult created = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Welcome",
                                  "subject":"Hi {{first_name}}",
                                  "htmlContent":"<p>Hello {{first_name}}</p>",
                                  "textContent":"Hello {{first_name}}",
                                  "variablesSchema":{"first_name":{"type":"string","required":true}}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andReturn();

        String templateId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/templates/" + templateId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEMPLATE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject":"Hi {{first_name}} v2",
                                  "htmlContent":"<p>Hello {{first_name}} v2</p>",
                                  "variablesSchema":{"first_name":{"type":"string","required":true}}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/versions/2/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(2));

        mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":"person@example.com",
                                  "templateId":"%s",
                                  "variables":{}
                                }
                                """.formatted(tenantSlug(tenantA), templateId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_TEMPLATE_VARIABLE"));
    }

    @Test
    void domainVerifySucceedsWithMockedDnsAndRejectsUnverifiedSender() throws Exception {
        String token = register();
        String slug = tenantSlug(token);

        MvcResult created = mockMvc.perform(post("/api/v1/domains")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"mail.example-phase4.test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String domainId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        MvcResult verification = mockMvc.perform(get("/api/v1/domains/" + domainId + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(4))
                .andExpect(jsonPath("$.data.records[0].privateKeyRef").doesNotExist())
                .andReturn();

        String body = verification.getResponse().getContentAsString();
        List<Map<String, Object>> records = JsonPath.read(body, "$.data.records");
        assertThat(records).hasSize(4);
        for (Map<String, Object> record : records) {
            String type = String.valueOf(record.get("type"));
            String value = String.valueOf(record.get("value"));
            assertThat(value).doesNotContain("texto-verify-");
            if (!"OWNERSHIP".equals(type)) {
                assertThat(value).doesNotContain("texto-domain-verification=");
            }
            txtRecords.put(String.valueOf(record.get("name")), List.of(value));
        }

        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));

        mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"ops@unverified.example",
                                  "to":"person@example.com",
                                  "subject":"Nope",
                                  "text":"body"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNVERIFIED_SENDER_DOMAIN"));

        mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":"person@example.com",
                                  "subject":"Platform ok",
                                  "text":"body"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void platformTestDomainIsProvisionedWithDkimRecords() throws Exception {
        String token = register();
        String slug = tenantSlug(token);
        String platformDomain = slug + ".texto.test";

        MvcResult listed = mockMvc.perform(get("/api/v1/domains").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].domain").value(platformDomain))
                .andExpect(jsonPath("$.data[0].status").value("VERIFIED"))
                .andReturn();
        String domainId = JsonPath.read(listed.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(get("/api/v1/domains/" + domainId + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(4))
                .andExpect(jsonPath("$.data.records[0].type").value("DKIM"))
                .andExpect(jsonPath("$.data.records[0].selector").value("texto"))
                .andExpect(jsonPath("$.data.records[0].privateKeyRef").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.records[0].value").value(org.hamcrest.Matchers.containsString("v=DKIM1; k=rsa; p=")));
    }

    @Test
    void dnsAuthenticationRejectsIncompleteAndMismatchedRecords() throws Exception {
        String token = register();
        String other = register();

        MvcResult created = mockMvc.perform(post("/api/v1/domains")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"auth.example-phase8.test"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String domainId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        MvcResult verification = mockMvc.perform(get("/api/v1/domains/" + domainId + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownershipStatus").value("PENDING"))
                .andReturn();
        Map<String, Map<String, Object>> byType = recordsByType(verification);

        mockMvc.perform(get("/api/v1/domains/" + domainId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/domains/" + domainId + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());

        Map<String, Object> ownership = byType.get("OWNERSHIP");
        txtRecords.put(String.valueOf(ownership.get("name")), List.of("texto-domain-verification=deadbeef"));
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.ownershipStatus").value("FAILED"));

        txtRecords.put(String.valueOf(ownership.get("name")), List.of(String.valueOf(ownership.get("value"))));
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"ops@auth.example-phase8.test",
                                  "to":"person@example.com",
                                  "subject":"Blocked",
                                  "text":"body"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNVERIFIED_SENDER_DOMAIN"));

        Map<String, Object> spf = byType.get("SPF");
        txtRecords.put(String.valueOf(spf.get("name")), List.of(
                "v=spf1 include:_spf.texto.email ~all",
                "v=spf1 include:elsewhere.example ~all"
        ));
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spfStatus").value("FAILED"));

        txtRecords.put(String.valueOf(spf.get("name")), List.of("v=spf1 include:_spf.texto.email ~all extra"));
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spfStatus").value("FAILED"));

        Map<String, Object> dkim = byType.get("DKIM");
        txtRecords.put(String.valueOf(dkim.get("name")), List.of("v=DKIM1; k=rsa; p=AAAA"));
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dkimStatus").value("FAILED"));

        Map<String, Object> dmarc = byType.get("DMARC");
        txtRecords.put(String.valueOf(dmarc.get("name")), List.of("v=DMARC1; pct=100"));
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dmarcStatus").value("FAILED"));

        when(dnsLookupService.lookupTxt(anyString())).thenReturn(DnsTxtQueryResult.nxdomain());
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        when(dnsLookupService.lookupTxt(anyString())).thenReturn(DnsTxtQueryResult.temporaryFailure());
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        when(dnsLookupService.lookupTxt(anyString())).thenReturn(DnsTxtQueryResult.malformed());
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        when(dnsLookupService.lookupTxt(anyString())).thenReturn(DnsTxtQueryResult.timeout());
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
        stubDns();
    }

    @Test
    void suppressionBlocksSendAndBounceCreatesSuppression() throws Exception {
        String token = register();
        String slug = tenantSlug(token);

        mockMvc.perform(post("/api/v1/suppressions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"blocked@example.com","reason":"manual"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MANUAL"));

        mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":"blocked@example.com",
                                  "subject":"Should suppress",
                                  "text":"body"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUPPRESSED"));

        when(deliveryEngine.deliver(any())).thenReturn(
                DeliveryEngine.DeliveryResult.permanent("smtp_550", "550 mailbox unavailable", "550")
        );

        MvcResult bouncedSend = mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":"bounce@example.com",
                                  "subject":"Bounce me",
                                  "text":"body"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        String bounceMessageId = JsonPath.read(bouncedSend.getResponse().getContentAsString(), "$.data.id");
        String tenantId = JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data.tenantId"
        );
        emailDeliveryWorker.process(UUID.fromString(bounceMessageId), UUID.fromString(tenantId), 1);

        mockMvc.perform(get("/api/v1/emails/" + bounceMessageId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BOUNCED"));

        mockMvc.perform(get("/api/v1/suppressions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "bounce@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("BOUNCE"));

        when(deliveryEngine.deliver(any())).thenReturn(
                DeliveryEngine.DeliveryResult.temporary("smtp_421", "421 try later", "421")
        );

        MvcResult deferredSend = mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":"temp@example.com",
                                  "subject":"Temp fail",
                                  "text":"body"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        String deferredMessageId = JsonPath.read(deferredSend.getResponse().getContentAsString(), "$.data.id");
        emailDeliveryWorker.process(UUID.fromString(deferredMessageId), UUID.fromString(tenantId), 1);

        mockMvc.perform(get("/api/v1/emails/" + deferredMessageId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEFERRED"));

        mockMvc.perform(get("/api/v1/suppressions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "temp@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void webhooksAreTenantIsolatedAndSecretShownOnce() throws Exception {
        String tenantA = register();
        String tenantB = register();
        upgradeToStarter(tenantA);

        MvcResult created = mockMvc.perform(post("/api/v1/webhooks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url":"https://example.com/webhooks/texto",
                                  "description":"Primary",
                                  "eventTypes":["email.delivered","email.bounced"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").value(org.hamcrest.Matchers.startsWith("whsec_")))
                .andExpect(jsonPath("$.data.webhook.secretPrefix").isNotEmpty())
                .andReturn();

        String body = created.getResponse().getContentAsString();
        String webhookId = JsonPath.read(body, "$.data.webhook.id");
        String secret = JsonPath.read(body, "$.data.secret");
        assertThat(secret).startsWith("whsec_");

        mockMvc.perform(get("/api/v1/webhooks/" + webhookId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").doesNotExist())
                .andExpect(jsonPath("$.data.secretPrefix").isNotEmpty());

        mockMvc.perform(get("/api/v1/webhooks/" + webhookId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WEBHOOK_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/webhooks/" + webhookId + "/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB))
                .andExpect(status().isNotFound());
    }

    @Test
    void suppressionsAreTenantIsolated() throws Exception {
        String tenantA = register();
        String tenantB = register();

        MvcResult created = mockMvc.perform(post("/api/v1/suppressions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a-only@example.com"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/suppressions/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/suppressions/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB))
                .andExpect(status().isNotFound());
    }

    private String register() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organization":"Phase4 Co","email":"%s","password":"password1"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private String tenantSlug(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String tenantId = JsonPath.read(me.getResponse().getContentAsString(), "$.data.tenantId");
        return jdbcTemplate.queryForObject("SELECT slug FROM tenants WHERE id = ?", String.class, UUID.fromString(tenantId));
    }

    private void upgradeToStarter(String token) throws Exception {
        String tenantId = JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data.tenantId"
        );
        UUID starterPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM plans WHERE code = 'STARTER'",
                UUID.class
        );
        jdbcTemplate.update(
                "UPDATE subscriptions SET plan_id = ? WHERE tenant_id = ?",
                starterPlanId,
                UUID.fromString(tenantId)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> recordsByType(MvcResult verification) throws Exception {
        List<Map<String, Object>> records = JsonPath.read(verification.getResponse().getContentAsString(), "$.data.records");
        Map<String, Map<String, Object>> byType = new java.util.LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            byType.put(String.valueOf(record.get("type")), record);
        }
        return byType;
    }
}
