package com.texto.emailplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.ops.domain.PlatformAdminEntity;
import com.texto.emailplatform.ops.domain.PlatformAdminRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class OpsConsoleIT {

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
    private PlatformAdminRepository platformAdminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private DeliveryEngine deliveryEngine;

    @BeforeEach
    void ensureAdmin() {
        if (platformAdminRepository.findFirstByEmailIgnoreCase("ops-admin@texto.test").isEmpty()) {
            platformAdminRepository.save(PlatformAdminEntity.create(
                    "ops-admin@texto.test",
                    passwordEncoder.encode("OpsAdmin1!"),
                    "Ops Admin"
            ));
        }
    }

    @Test
    void adminApisRejectAnonymousTenantAndApiKeyCallers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        String tenantToken = register("ops-tenant-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        MvcResult created = mockMvc.perform(post("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ops-test\",\"environment\":\"TEST\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String apiKey = JsonPath.read(created.getResponse().getContentAsString(), "$.data.secret");

        mockMvc.perform(get("/api/v1/admin/emails").header("X-Api-Key", apiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void platformAdminCanReadAcrossTenantsWithoutBodiesOrSecrets() throws Exception {
        String tenantA = register("ops-a-" + UUID.randomUUID() + "@example.com");
        String tenantB = register("ops-b-" + UUID.randomUUID() + "@example.com");
        UUID tenantAId = UUID.fromString(JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data.tenantId"
        ));
        UUID tenantBId = UUID.fromString(JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data.tenantId"
        ));

        mockMvc.perform(post("/api/v1/emails/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"owner@%s.texto.test","to":"a@example.com","subject":"alpha secret body","body":"TENANT_A_BODY"}
                                """.formatted(slug(tenantA))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/emails/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"owner@%s.texto.test","to":"b@example.com","subject":"beta","body":"TENANT_B_BODY"}
                                """.formatted(slug(tenantB))))
                .andExpect(status().isOk());

        String adminToken = adminLogin();

        mockMvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMessages").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.queued").isNumber())
                .andExpect(jsonPath("$.data.deliveryRate").isNumber());

        MvcResult all = mockMvc.perform(get("/api/v1/admin/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].htmlBody").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].textBody").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].bounceCorrelationToken").doesNotExist())
                .andReturn();
        String allBody = all.getResponse().getContentAsString();
        assertThat(allBody).doesNotContain("TENANT_A_BODY").doesNotContain("TENANT_B_BODY");

        mockMvc.perform(get("/api/v1/admin/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("tenantId", tenantAId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].tenantId").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(tenantAId.toString())
                )));

        mockMvc.perform(get("/api/v1/admin/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA)
                        .param("tenantId", tenantBId.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/tenants").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/v1/admin/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].secret").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/api/v1/admin/queues").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queues").isArray());

        mockMvc.perform(get("/api/v1/admin/outbox").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/bounces").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/complaints").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/suppressions").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/domains").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/delivery-attempts").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/webhooks/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/tenants/" + tenantAId + "/usage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("FREE"));

        mockMvc.perform(get("/api/v1/admin/audit").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/v1/admin/emails").param("tenantId", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void tenantEmailListStillIsolated() throws Exception {
        String tenantA = register("iso-a-" + UUID.randomUUID() + "@example.com");
        String tenantB = register("iso-b-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(post("/api/v1/emails/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"owner@%s.texto.test","to":"b@example.com","subject":"only-b","body":"hidden"}
                                """.formatted(slug(tenantB))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/emails").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].subject").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("only-b")
                )));
    }

    private String adminLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ops-admin@texto.test","password":"OpsAdmin1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.admin.role").value("PLATFORM_ADMIN"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organization":"Ops Org %s","email":"%s","password":"password1"}
                                """.formatted(UUID.randomUUID(), email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private String slug(String token) throws Exception {
        return JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data.tenantSlug"
        );
    }
}
