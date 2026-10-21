package com.texto.emailplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.queue.EmailQueues;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
class PlatformFoundationIT {

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
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private DeliveryEngine deliveryEngine;


    @Test
    void contextStartsAndInfrastructureIsReachable() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class
        );
        assertThat(migrations).isNotNull().isGreaterThanOrEqualTo(5);

        Integer planCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plans WHERE active", Integer.class);
        assertThat(planCount).isEqualTo(4);

        Boolean queueExists = rabbitTemplate.execute(channel -> {
            channel.queueDeclarePassive(EmailQueues.OUTBOUND_QUEUE);
            return true;
        });
        assertThat(queueExists).isTrue();

        try (var connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

    @Test
    void healthEndpointReportsApplicationDatabaseRabbitAndRedis() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.rabbit.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }

    @Test
    void plansAreLoadedFromTheDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].code").value("FREE"))
                .andExpect(jsonPath("$.data[0].features.API_SENDING").value(true))
                .andExpect(jsonPath("$.data[0].limits.MONTHLY_EMAILS").value(500))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void protectedApisRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/emails"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void registerLoginAndSendTestEmail() throws Exception {
        String email = "owner-" + java.util.UUID.randomUUID() + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organization":"Get Set Go World","email":"%s","password":"password1"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.user.tenantSlug").value("get-set-go-world"))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(get("/api/v1/emails").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(post("/api/v1/emails/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"noreply@get-set-go-world.texto.test","to":"%s","subject":"Hello","body":"Test body"}
                                        """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.recipient").value(email));
    }

    @Test
    void campaignReportSeparatesQueueAcceptanceBounceAndComplaint() throws Exception {
        String ownerEmail = "campaign-owner-" + java.util.UUID.randomUUID() + "@example.com";
        String organization = "Campaign Report " + java.util.UUID.randomUUID();
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organization":"%s","email":"%s","password":"password1"}
                                """.formatted(organization, ownerEmail)))
                .andExpect(status().isOk())
                .andReturn();

        String response = registration.getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(response, "$.data.token");
        java.util.UUID tenantId = java.util.UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(response, "$.data.user.tenantId")
        );
        String recipientEmail = "campaign-recipient-" + java.util.UUID.randomUUID() + "@example.com";

        MvcResult draftResult = mockMvc.perform(post("/api/v1/campaigns/drafts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Report fixture","sender":"noreply@example.com","subject":"Report","textBody":"Report"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        java.util.UUID campaignId = java.util.UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(draftResult.getResponse().getContentAsString(), "$.data.id")
        );

        MvcResult contactResult = mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","firstName":"Report","properties":"{}"}
                                """.formatted(recipientEmail)))
                .andExpect(status().isOk())
                .andReturn();
        java.util.UUID contactId = java.util.UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(contactResult.getResponse().getContentAsString(), "$.data.id")
        );

        MvcResult recipientsResult = mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/recipients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactIds\":[\"%s\"]}".formatted(contactId)))
                .andReturn();
        assertThat(recipientsResult.getResponse().getStatus())
                .withFailMessage(recipientsResult.getResponse().getContentAsString())
                .isEqualTo(200);

        java.util.UUID messageId = java.util.UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO email_messages (id, tenant_id, status, recipient, subject, created_at, updated_at)
                VALUES (?, ?, 'QUEUED', ?, 'Report', now(), now())
                """, messageId, tenantId, recipientEmail);
        jdbcTemplate.update("""
                UPDATE campaign_recipients
                   SET status = 'ACCEPTED', email_message_id = ?
                 WHERE campaign_id = ? AND tenant_id = ?
                """, messageId, campaignId, tenantId);

        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.queued").value(1))
                .andExpect(jsonPath("$.data.accepted").value(0))
                .andExpect(jsonPath("$.data.delivered").value(0));

        jdbcTemplate.update("UPDATE email_messages SET status = 'DELIVERED' WHERE id = ?", messageId);
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(1))
                .andExpect(jsonPath("$.data.delivered").value(0));

        jdbcTemplate.update("UPDATE email_messages SET status = 'BOUNCED' WHERE id = ?", messageId);
        jdbcTemplate.update("""
                INSERT INTO complaint_events (
                    id, tenant_id, email_message_id, event_hash, correlation_status,
                    processing_status, provider, complaint_type, received_at, created_at
                ) VALUES (?, ?, ?, ?, 'MATCHED', 'APPLIED', 'ses', 'SPAM', now(), now())
                """, java.util.UUID.randomUUID(), tenantId, messageId, "a".repeat(64));

        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bounced").value(1))
                .andExpect(jsonPath("$.data.complained").value(1))
                .andExpect(jsonPath("$.data.delivered").value(0));
    }

    @Test
    void corsAllowsIdempotencyKeyFromLocalFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/emails")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
    }
}
