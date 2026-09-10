package com.texto.emailplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.usage.BillingPeriod;
import com.texto.emailplatform.usage.UsageMetrics;
import com.texto.emailplatform.usage.UsageService;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
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
class SaaSCoreIT {

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
    private UsageService usageService;

    @MockitoBean
    private DeliveryEngine deliveryEngine;

    @Test
    void registrationCreatesSubscriptionEntitlementsAndUsage() throws Exception {
        String token = register();

        mockMvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("FREE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.currentPeriodStart").isNotEmpty())
                .andExpect(jsonPath("$.data.currentPeriodEnd").isNotEmpty());

        mockMvc.perform(get("/api/v1/entitlements").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("FREE"))
                .andExpect(jsonPath("$.data.features.API_SENDING").value(true))
                .andExpect(jsonPath("$.data.features.CAMPAIGNS").value(false))
                .andExpect(jsonPath("$.data.limits.MONTHLY_EMAILS").value(500))
                .andExpect(jsonPath("$.data.limits.API_KEYS").value(2));

        mockMvc.perform(get("/api/v1/usage").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.length()").value(UsageMetrics.ALL.size()))
                .andExpect(jsonPath("$.data.metrics[0].metric").value(UsageMetrics.MONTHLY_EMAILS))
                .andExpect(jsonPath("$.data.metrics[0].used").value(0))
                .andExpect(jsonPath("$.data.metrics[0].limit").value(500))
                .andExpect(jsonPath("$.data.metrics[0].remaining").value(500));
    }

    @Test
    void apiKeySecretIsReturnedOnceAndNeverListed() throws Exception {
        String token = register();

        MvcResult created = createApiKey(token, "Primary key");
        String body = created.getResponse().getContentAsString();
        String secret = JsonPath.read(body, "$.data.secret");
        String prefix = JsonPath.read(body, "$.data.apiKey.keyPrefix");

        assertThat(secret).startsWith("sk_test_");
        assertThat(prefix).isEqualTo(secret.substring(0, 12));

        mockMvc.perform(get("/api/v1/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].keyPrefix").value(prefix))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist());

        Integer storedSecrets = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_keys WHERE key_hash = ?",
                Integer.class,
                secret
        );
        assertThat(storedSecrets).isZero();
    }

    @Test
    void apiKeysAreScopedToTheOwningTenant() throws Exception {
        String tenantAToken = register();
        String tenantBToken = register();

        MvcResult created = createApiKey(tenantAToken, "Tenant A key");
        String apiKeyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.apiKey.id");

        mockMvc.perform(get("/api/v1/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/api-keys/" + apiKeyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("API_KEY_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/api-keys/" + apiKeyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(apiKeyId));
    }

    @Test
    void revokingAnApiKeyStopsItFromAuthenticating() throws Exception {
        String token = register();

        MvcResult created = createApiKey(token, "Disposable key");
        String body = created.getResponse().getContentAsString();
        String secret = JsonPath.read(body, "$.data.secret");
        String apiKeyId = JsonPath.read(body, "$.data.apiKey.id");

        mockMvc.perform(get("/api/v1/emails").header("X-Api-Key", secret))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/api-keys/" + apiKeyId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.revokedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/emails").header("X-Api-Key", secret))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"));
    }

    @Test
    void apiKeyCreationStopsAtThePlanLimit() throws Exception {
        String token = register();

        createApiKey(token, "Key one").getResponse();
        createApiKey(token, "Key two").getResponse();

        mockMvc.perform(post("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Key three"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("QUOTA_EXCEEDED"));
    }

    @Test
    void logoutInvalidatesTheSessionToken() throws Exception {
        String token = register();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void loginFailuresReturnAGenericError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody-%s@example.com","password":"password1"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void concurrentQuotaConsumptionNeverExceedsThePlanLimit() throws Exception {
        String token = register();
        UUID tenantId = UUID.fromString(JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data.tenantId"
        ));

        long limit = 500L;
        long headroom = 3L;
        jdbcTemplate.update(
                "UPDATE tenant_usage SET used = ? WHERE tenant_id = ? AND period_start = ? AND metric = ?",
                limit - headroom,
                tenantId,
                java.sql.Date.valueOf(BillingPeriod.current().start()),
                UsageMetrics.MONTHLY_EMAILS
        );

        int threads = 12;
        CountDownLatch startGate = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            attempts.add(() -> {
                startGate.await(10, TimeUnit.SECONDS);
                return usageService.tryConsumeQuota(tenantId, UsageMetrics.MONTHLY_EMAILS, 1);
            });
        }

        long granted;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            attempts.forEach(attempt -> futures.add(executor.submit(attempt)));
            startGate.countDown();
            granted = futures.stream().filter(SaaSCoreIT::resolve).count();
        }

        assertThat(granted).isEqualTo(headroom);

        Long used = jdbcTemplate.queryForObject(
                "SELECT used FROM tenant_usage WHERE tenant_id = ? AND period_start = ? AND metric = ?",
                Long.class,
                tenantId,
                java.sql.Date.valueOf(BillingPeriod.current().start()),
                UsageMetrics.MONTHLY_EMAILS
        );
        assertThat(used).isEqualTo(limit);
    }

    private static boolean resolve(Future<Boolean> future) {
        try {
            return Boolean.TRUE.equals(future.get(30, TimeUnit.SECONDS));
        } catch (Exception exception) {
            throw new IllegalStateException("Quota attempt failed", exception);
        }
    }

    private String register() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organization":"Texto QA","email":"%s","password":"password1"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private MvcResult createApiKey(String token, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").isNotEmpty())
                .andReturn();
    }
}
