package com.texto.emailplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.delivery.DkimSigner;
import com.texto.emailplatform.delivery.SmtpDeliveryEngine;
import com.texto.emailplatform.delivery.mta.MtaClient;
import com.texto.emailplatform.delivery.mta.MtaOutcome;
import com.texto.emailplatform.delivery.mta.MtaResult;
import com.texto.emailplatform.delivery.mta.MtaSubmitRequest;
import com.texto.emailplatform.delivery.mta.PostfixMtaClient;
import com.texto.emailplatform.delivery.mta.PostfixTestContainer;
import com.texto.emailplatform.delivery.mta.SmtpEnvelope;
import com.texto.emailplatform.email.EmailDeliveryWorker;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.outbox.OutboxPublisher;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
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
class PostfixMtaIT {

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
    static GenericContainer<?> postfix = PostfixTestContainer.create();

    @DynamicPropertySource
    static void postfixProperties(DynamicPropertyRegistry registry) {
        registry.add("email-platform.mta.implementation", () -> "postfix");
        registry.add("email-platform.mta.smtp.host", postfix::getHost);
        registry.add("email-platform.mta.smtp.port", postfix::getFirstMappedPort);
        registry.add("email-platform.mta.smtp.starttls.enabled", () -> "false");
        registry.add("email-platform.mta.smtp.starttls.required", () -> "false");
        registry.add("email-platform.mta.smtp.ssl.enabled", () -> "false");
        registry.add("email-platform.mta.smtp.ehlo-hostname", () -> "texto.local");
    }

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

    @Autowired
    private MtaClient mtaClient;

    @Autowired
    private DeliveryEngine deliveryEngine;

    @Autowired
    private com.texto.emailplatform.common.config.EmailPlatformProperties properties;

    @Test
    void applicationStartsWithPostfixImplementation() {
        assertThat(deliveryEngine).isInstanceOf(SmtpDeliveryEngine.class);
        assertThat(mtaClient).isInstanceOf(PostfixMtaClient.class);
        assertThat(properties.getMta().getImplementation()).isEqualToIgnoringCase("postfix");
        assertThat(properties.getMta().getSmtp().getHost()).isEqualTo(postfix.getHost());
        assertThat(properties.getMta().getSmtp().getPort()).isEqualTo(postfix.getMappedPort(25));
        assertThat(properties.getMta().getSmtp().getStarttls().isEnabled()).isFalse();
        assertThat(properties.getMta().getSmtp().getStarttls().isRequired()).isFalse();
    }

    @Test
    void pipelineSignsDkimAndPostfixAcceptsLocalTextoTestMail() throws Exception {
        String token = register();
        String slug = tenantSlug(token);
        String tenantId = currentTenantId(token);

        MvcResult sent = mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":["alice@texto.test"],
                                  "subject":"Postfix local",
                                  "text":"controlled local sink"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();

        String messageId = JsonPath.read(sent.getResponse().getContentAsString(), "$.data.id");
        outboxPublisher.publishPending();
        emailDeliveryWorker.process(UUID.fromString(messageId), UUID.fromString(tenantId), 1);

        EmailMessageEntity message = emailMessageRepository.findById(UUID.fromString(messageId)).orElseThrow();
        assertThat(deliveryEngine).isInstanceOf(SmtpDeliveryEngine.class);
        assertThat(message.getStatus())
                .as("providerResponse=%s providerMessageId=%s", message.getProviderResponse(), message.getProviderMessageId())
                .isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(message.getDeliveredAt()).isNotNull();
        assertThat(message.getProviderResponse()).containsIgnoringCase("queued as");

        String stored = waitForMaildirMessage(message.getProviderResponse());
        assertThat(stored).contains("DKIM-Signature:");
        assertThat(stored).contains("controlled local sink");
        assertThat(DkimSigner.hasDkimSignature(stored)).isTrue();
        assertThat(DkimSigner.verify(publicKeyForTenant(tenantId), stored)).isTrue();
    }

    @Test
    void postfixRejectsUnauthorizedExternalDestination() throws Exception {
        MtaResult result = mtaClient.submit(new MtaSubmitRequest(
                new SmtpEnvelope("noreply@acme.texto.test", List.of("probe@example.com")),
                "From: noreply@acme.texto.test\r\nTo: probe@example.com\r\nSubject: relay-probe\r\n\r\nno\r\n"
                        .getBytes(StandardCharsets.UTF_8)
        ));
        assertThat(result.outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
        assertThat(result.smtpCode()).startsWith("5");

        try (Socket socket = new Socket(postfix.getHost(), postfix.getMappedPort(25));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            assertThat(in.readLine()).startsWith("220");
            out.write("EHLO texto.local\r\n");
            out.flush();
            drainEhlo(in);
            out.write("MAIL FROM:<noreply@acme.texto.test>\r\n");
            out.flush();
            assertThat(in.readLine()).startsWith("250");
            out.write("RCPT TO:<probe@example.com>\r\n");
            out.flush();
            String rcpt = in.readLine();
            assertThat(rcpt).startsWith("5");
            out.write("QUIT\r\n");
            out.flush();
        }
    }

    private String waitForMaildirMessage(String providerResponse) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            var listing = postfix.execInContainer(
                    "sh",
                    "-c",
                    "find /var/mail -type f \\( -path '*/new/*' -o -path '*/cur/*' \\) | head -n 1"
            );
            String path = listing.getStdout() == null ? "" : listing.getStdout().trim();
            if (!path.isBlank()) {
                return postfix.execInContainer("cat", path).getStdout();
            }
            Thread.sleep(250);
        }
        String queue = postfix.execInContainer("postqueue", "-p").getStdout();
        String tree = postfix.execInContainer("sh", "-c", "ls -laR /var/mail").getStdout();
        throw new AssertionError(
                "Postfix maildir stayed empty. Queue: " + queue
                        + " Tree: " + tree
                        + " smtp=" + properties.getMta().getSmtp().getHost()
                        + ":" + properties.getMta().getSmtp().getPort()
                        + " providerResponse=" + providerResponse
                        + " engine=" + deliveryEngine.getClass().getName()
        );
    }

    private PublicKey publicKeyForTenant(String tenantId) throws Exception {
        String stored = jdbcTemplate.queryForObject(
                """
                        SELECT dvr.private_key_ref
                        FROM domain_verification_records dvr
                        JOIN domains d ON d.id = dvr.domain_id
                        WHERE d.tenant_id = ? AND dvr.type = 'DKIM'
                        """,
                String.class,
                UUID.fromString(tenantId)
        );
        assertThat(stored).startsWith("pkcs8:");
        byte[] der = Base64.getDecoder().decode(stored.substring("pkcs8:".length()));
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
    }

    private static void drainEhlo(BufferedReader in) throws Exception {
        String line = in.readLine();
        assertThat(line).startsWith("250");
        while (line != null && line.length() >= 4 && line.charAt(3) == '-') {
            line = in.readLine();
        }
    }

    private String register() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organization":"Postfix Co","email":"%s","password":"password1"}
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
