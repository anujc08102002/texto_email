package com.texto.emailplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.email.EmailDeliveryWorker;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.outbox.OutboxPublisher;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
class Phase5EmailPipelineIT {

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
    private OutboxPublisher outboxPublisher;

    @Autowired
    private EmailDeliveryWorker emailDeliveryWorker;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @MockitoBean
    private DeliveryEngine deliveryEngine;

    @BeforeEach
    void stubDeliverySuccess() {
        Mockito.reset(deliveryEngine);
        when(deliveryEngine.deliver(any())).thenReturn(DeliveryEngine.DeliveryResult.success("<id@texto.local>", "250 Ok"));
    }

    @Test
    void sendQueuesThenOutboxAndWorkerDeliver() throws Exception {
        String token = register();
        String slug = tenantSlug(token);

        MvcResult sent = mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":["person@example.com"],
                                  "subject":"Pipeline",
                                  "text":"hello"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();

        String messageId = JsonPath.read(sent.getResponse().getContentAsString(), "$.data.id");
        String tenantId = currentTenantId(token);

        Integer unpublished = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL",
                Integer.class,
                UUID.fromString(messageId)
        );
        assertThat(unpublished).isEqualTo(1);

        outboxPublisher.publishPending();

        Integer stillUnpublished = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL",
                Integer.class,
                UUID.fromString(messageId)
        );
        assertThat(stillUnpublished).isZero();

        // Listeners are disabled in the test profile — drive the worker directly.
        emailDeliveryWorker.process(UUID.fromString(messageId), UUID.fromString(tenantId), 1);

        EmailMessageEntity message = emailMessageRepository.findById(UUID.fromString(messageId)).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(message.getDeliveredAt()).isNotNull();
        assertThat(message.getAttemptCount()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/emails/" + messageId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.attempts.length()").value(1))
                .andExpect(jsonPath("$.data.attempts[0].status").value("SUCCESS"));
    }

    @Test
    void idempotencyKeyReturnsSameMessage() throws Exception {
        String token = register();
        String slug = tenantSlug(token);
        String key = "idem-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":["one@example.com"],
                                  "subject":"Idem",
                                  "text":"body"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":["one@example.com"],
                                  "subject":"Idem",
                                  "text":"body"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstId))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void allSuppressedReturnsSuppressedWithoutDelivery() throws Exception {
        String token = register();
        String slug = tenantSlug(token);

        mockMvc.perform(post("/api/v1/suppressions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"blocked@example.com","reason":"manual"}
                                """))
                .andExpect(status().isOk());

        MvcResult suppressed = mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":["blocked@example.com"],
                                  "subject":"Nope",
                                  "text":"body"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUPPRESSED"))
                .andReturn();

        String messageId = JsonPath.read(suppressed.getResponse().getContentAsString(), "$.data.id");
        Integer outboxForMessage = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class,
                UUID.fromString(messageId)
        );
        assertThat(outboxForMessage).isZero();
    }

    @Test
    void tenantIsolationOnGet() throws Exception {
        String tenantA = register();
        String tenantB = register();
        String slugA = tenantSlug(tenantA);

        MvcResult sent = mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":["a@example.com"],
                                  "subject":"Private",
                                  "text":"body"
                                }
                                """.formatted(slugA)))
                .andExpect(status().isOk())
                .andReturn();
        String messageId = JsonPath.read(sent.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/emails/" + messageId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_FOUND"));
    }

    @Test
    void temporaryFailureSchedulesRetry() throws Exception {
        String token = register();
        String slug = tenantSlug(token);
        String tenantId = currentTenantId(token);

        when(deliveryEngine.deliver(any())).thenReturn(
                DeliveryEngine.DeliveryResult.temporary("smtp_421", "421 try later", "421")
        );

        MvcResult sent = mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":["temp@example.com"],
                                  "subject":"Retry",
                                  "text":"body"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        String messageId = JsonPath.read(sent.getResponse().getContentAsString(), "$.data.id");

        outboxPublisher.publishPending();
        // Listeners are disabled in the test profile — drive the worker directly.
        emailDeliveryWorker.process(UUID.fromString(messageId), UUID.fromString(tenantId), 1);

        EmailMessageEntity message = emailMessageRepository.findById(UUID.fromString(messageId)).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DEFERRED);
        assertThat(message.getNextAttemptAt()).isNotNull();
        assertThat(message.getAttemptCount()).isEqualTo(1);
    }

    private String register() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organization":"Phase5 Co","email":"%s","password":"password1"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private String tenantSlug(String token) throws Exception {
        String tenantId = currentTenantId(token);
        return jdbcTemplate.queryForObject("SELECT slug FROM tenants WHERE id = ?", String.class, UUID.fromString(tenantId));
    }

    private String currentTenantId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(me.getResponse().getContentAsString(), "$.data.tenantId");
    }
}
