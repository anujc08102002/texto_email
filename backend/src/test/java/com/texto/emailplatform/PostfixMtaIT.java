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
import com.texto.emailplatform.domain.DkimKeyMaterial;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.outbox.OutboxPublisher;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
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
        registry.add("management.health.mta.enabled", () -> "true");
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

    @Autowired
    private com.texto.emailplatform.bounce.BounceDsnWorker bounceDsnWorker;

    @Autowired
    private com.texto.emailplatform.bounce.domain.BounceEventRepository bounceEventRepository;

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

        String stored = waitForMaildirFile("/var/mail/texto.test");
        assertThat(stored).contains("DKIM-Signature:");
        assertThat(stored).contains("controlled local sink");
        assertThat(stored).contains("From: noreply@" + slug + ".texto.test");
        assertThat(message.getBounceCorrelationToken()).isNotBlank();
        assertThat(stored).containsIgnoringCase("Return-Path: <bounce+" + message.getBounceCorrelationToken() + "@bounce.texto.test>");
        assertThat(DkimSigner.hasDkimSignature(stored)).isTrue();
        assertThat(DkimSigner.verify(publicKeyForTenant(tenantId), stored)).isTrue();

        mockMvc.perform(get("/api/v1/emails/" + messageId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(messageId))
                .andExpect(jsonPath("$.data.bounceCorrelationToken").doesNotExist())
                .andExpect(jsonPath("$.data.bounce_correlation_token").doesNotExist());
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

    @Test
    void postfixSmtpListenerReturnsRfc5321Greeting() throws Exception {
        try (Socket socket = new Socket(postfix.getHost(), postfix.getMappedPort(25));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            String banner = in.readLine();
            assertThat(banner).startsWith("220");
            assertThat(banner).containsIgnoringCase("mail.texto.test");
            out.write("QUIT\r\n");
            out.flush();
        }
    }

    @Test
    void postfixAcceptsControlledBounceAddressAndRejectsUnexpectedBounceLocalPart() throws Exception {
        String token = "0123456789abcdef0123456789abcdef";
        try (Socket socket = new Socket(postfix.getHost(), postfix.getMappedPort(25));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            assertThat(in.readLine()).startsWith("220");
            out.write("EHLO texto.local\r\n");
            out.flush();
            drainEhlo(in);
            out.write("MAIL FROM:<>\r\n");
            out.flush();
            assertThat(in.readLine()).startsWith("250");
            out.write("RCPT TO:<bounce+" + token + "@bounce.texto.test>\r\n");
            out.flush();
            assertThat(in.readLine()).startsWith("250");
            out.write("RCPT TO:<bounce@bounce.texto.test>\r\n");
            out.flush();
            assertThat(in.readLine()).startsWith("5");
            out.write("QUIT\r\n");
            out.flush();
        }
    }

    @Test
    void trustedPostfixBouncePathAppliesHardBouncePolicy() throws Exception {
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
                                  "subject":"Bounce path",
                                  "text":"dsn-correlation"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andReturn();
        String messageId = JsonPath.read(sent.getResponse().getContentAsString(), "$.data.id");
        outboxPublisher.publishPending();
        emailDeliveryWorker.process(UUID.fromString(messageId), UUID.fromString(tenantId), 1);

        EmailMessageEntity message = emailMessageRepository.findById(UUID.fromString(messageId)).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        String bounceToken = message.getBounceCorrelationToken();
        String rfc822Id = message.getRfc822MessageId();
        assertThat(bounceToken).isNotBlank();
        assertThat(rfc822Id).isNotBlank();

        byte[] dsn = com.texto.emailplatform.bounce.DsnFixtures.hardBounce(rfc822Id, "alice@texto.test");
        smtpInject(dsn, "bounce+" + bounceToken + "@bounce.texto.test");
        String storedDsn = waitForMaildirFile("/var/mail/bounce");
        assertThat(storedDsn).contains("message/delivery-status");

        var result = bounceDsnWorker.process("bounce+" + bounceToken + "@bounce.texto.test", dsn);
        assertThat(result.outcome()).isEqualTo(com.texto.emailplatform.bounce.DsnIngestionResult.Outcome.ACCEPTED);
        assertThat(result.events().getFirst().getEmailMessageId()).isEqualTo(message.getId());
        assertThat(result.events().getFirst().getTenantId()).isEqualTo(UUID.fromString(tenantId));
        assertThat(result.events().getFirst().getCorrelationStatus()).isEqualTo("MATCHED");

        EmailMessageEntity after = emailMessageRepository.findById(message.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(EmailMessageEntity.STATUS_BOUNCED);
        assertThat(after.getBouncedRecipients()).contains("alice@texto.test");
        assertThat(bounceEventRepository.findByTenantIdAndEmailMessageId(
                UUID.fromString(tenantId),
                message.getId()
        )).isNotEmpty();

        mockMvc.perform(get("/api/v1/suppressions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "alice@texto.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("BOUNCE"))
                .andExpect(jsonPath("$.data[0].reason").value("HARD_BOUNCE"));

        mockMvc.perform(post("/api/v1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from":"noreply@%s.texto.test",
                                  "to":["alice@texto.test"],
                                  "subject":"Should suppress",
                                  "text":"no"
                                }
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUPPRESSED"));
    }

    @Test
    void postfixIsNotAnOpenRelayAndRejectsUnauthDestinationAtRcpt() throws Exception {
        String limit = postfix.execInContainer("postconf", "-h", "smtpd_relay_restrictions").getStdout().trim();
        assertThat(limit).isEqualTo("reject_unauth_destination");
        String networks = postfix.execInContainer("postconf", "-h", "mynetworks").getStdout();
        assertThat(networks).doesNotContain("0.0.0.0/0");
        String transport = postfix.execInContainer("postconf", "-h", "default_transport").getStdout();
        assertThat(transport).contains("public Internet delivery is disabled");
    }

    @Test
    void messageSizeLimitIsAlignedWithApplication() throws Exception {
        String postfixLimit = postfix.execInContainer("postconf", "-h", "message_size_limit").getStdout().trim();
        assertThat(postfixLimit).isEqualTo("10485760");
        assertThat(properties.getEmail().getMaxRfc822Bytes()).isEqualTo(10_485_760);
        assertThat(properties.getEmail().getMaxRfc822Bytes()).isEqualTo(Integer.parseInt(postfixLimit));
    }

    @Test
    void mtaHealthIsReachableNotRecipientDelivery() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.mta.status").value("UP"))
                .andExpect(jsonPath("$.components.mta.details.reachable").value(true))
                .andExpect(jsonPath("$.components.mta.details.recipientDelivery").value("not_checked"));
    }

    private String waitForMaildirFile(String root) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            var listing = postfix.execInContainer(
                    "sh",
                    "-c",
                    "find " + root + " -type f \\( -path '*/new/*' -o -path '*/cur/*' \\) | head -n 1"
            );
            String path = listing.getStdout() == null ? "" : listing.getStdout().trim();
            if (!path.isBlank()) {
                return postfix.execInContainer("cat", path).getStdout();
            }
            Thread.sleep(250);
        }
        String queue = postfix.execInContainer("postqueue", "-p").getStdout();
        String tree = postfix.execInContainer("sh", "-c", "ls -laR /var/mail").getStdout();
        throw new AssertionError("Maildir " + root + " stayed empty. Queue: " + queue + " Tree: " + tree);
    }

    private void smtpInject(byte[] rfc822, String recipient) throws Exception {
        try (Socket socket = new Socket(postfix.getHost(), postfix.getMappedPort(25));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            assertThat(in.readLine()).startsWith("220");
            out.write("EHLO texto.local\r\n");
            out.flush();
            drainEhlo(in);
            out.write("MAIL FROM:<>\r\n");
            out.flush();
            assertThat(in.readLine()).startsWith("250");
            out.write("RCPT TO:<" + recipient + ">\r\n");
            out.flush();
            assertThat(in.readLine()).startsWith("250");
            out.write("DATA\r\n");
            out.flush();
            assertThat(in.readLine()).startsWith("354");
            String payload = new String(rfc822, StandardCharsets.US_ASCII).replace("\r\n", "\n").replace('\n', '\n');
            for (String line : payload.split("\n", -1)) {
                if (line.startsWith(".")) {
                    out.write("." + line + "\r\n");
                } else {
                    out.write(line + "\r\n");
                }
            }
            out.write(".\r\n");
            out.flush();
            assertThat(in.readLine()).startsWith("250");
            out.write("QUIT\r\n");
            out.flush();
        }
    }

    private PublicKey publicKeyForTenant(String tenantId) {
        UUID tenant = UUID.fromString(tenantId);
        String publicKey = jdbcTemplate.queryForObject(
                """
                        SELECT dk.public_key
                        FROM dkim_keys dk
                        JOIN domains d ON d.id = dk.domain_id
                        WHERE d.tenant_id = ? AND dk.status = 'ACTIVE'
                        """,
                String.class,
                tenant
        );
        String encrypted = jdbcTemplate.queryForObject(
                """
                        SELECT dk.encrypted_private_key
                        FROM dkim_keys dk
                        JOIN domains d ON d.id = dk.domain_id
                        WHERE d.tenant_id = ? AND dk.status = 'ACTIVE'
                        """,
                String.class,
                tenant
        );
        String keyRef = jdbcTemplate.queryForObject(
                """
                        SELECT dvr.private_key_ref
                        FROM domain_verification_records dvr
                        JOIN domains d ON d.id = dvr.domain_id
                        WHERE d.tenant_id = ? AND dvr.type = 'DKIM'
                        """,
                String.class,
                tenant
        );
        assertThat(encrypted).startsWith("dk1:");
        assertThat(encrypted).doesNotStartWith("pkcs8:");
        assertThat(keyRef).startsWith("dkim-key:");
        return DkimKeyMaterial.publicKeyFromPkcs1(publicKey);
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
