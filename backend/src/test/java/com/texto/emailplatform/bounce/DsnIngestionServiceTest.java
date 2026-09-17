package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.bounce.domain.BounceEventEntity;
import com.texto.emailplatform.bounce.domain.BounceEventRepository;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DsnIngestionServiceTest {

    @Mock
    private BounceEventRepository bounceEventRepository;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private SuppressionService suppressionService;

    @Mock
    private WebhookEventPublisher webhookEventPublisher;

    private DsnIngestionService service;
    private UUID tenantA;
    private UUID tenantB;
    private EmailMessageEntity tenantAMessage;
    private EmailMessageEntity tenantBMessage;

    @BeforeEach
    void setUp() {
        tenantA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        tenantB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        tenantAMessage = delivered(tenantA, "<abc@texto.local>", List.of("alice@example.com"));
        tenantBMessage = delivered(tenantB, "<bbb@texto.local>", List.of("alice@example.com"));
        BounceCorrelator correlator = new BounceCorrelator(
                emailMessageRepository,
                new BounceTokenExtractor(new EmailPlatformProperties())
        );
        BounceMetrics metrics = new BounceMetrics(new SimpleMeterRegistry());
        BounceApplicationService application = new BounceApplicationService(
                emailMessageRepository,
                suppressionService,
                webhookEventPublisher,
                metrics
        );
        service = new DsnIngestionService(
                new DsnParser(new EmailPlatformProperties()),
                correlator,
                bounceEventRepository,
                application,
                metrics
        );
        org.mockito.Mockito.lenient().when(bounceEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(bounceEventRepository.findByEventHash(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(emailMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void hardBounceMarksBouncedAndSuppressesRecipient() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        DsnIngestionResult result = service.ingest(new DsnIngestionRequest(
                DsnFixtures.hardBounce("<abc@texto.local>", "alice@example.com"),
                bounceAddress(tenantAMessage)
        ));

        assertThat(result.outcome()).isEqualTo(DsnIngestionResult.Outcome.ACCEPTED);
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_BOUNCED);
        assertThat(tenantAMessage.getBouncedRecipients()).contains("alice@example.com");
        verify(suppressionService).recordBounce(
                tenantA,
                "alice@example.com",
                tenantAMessage.getId(),
                BounceApplicationService.REASON_HARD_BOUNCE
        );
        verify(webhookEventPublisher).publishEmailEvent("email.bounced", tenantAMessage);
    }

    @Test
    void softBounceDoesNotSuppressAndDoesNotRequeue() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        DsnIngestionResult result = service.ingest(new DsnIngestionRequest(
                DsnFixtures.softBounce("<abc@texto.local>", "alice@example.com"),
                bounceAddress(tenantAMessage)
        ));

        assertThat(result.outcome()).isEqualTo(DsnIngestionResult.Outcome.ACCEPTED);
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(tenantAMessage.getSoftBouncedRecipients()).contains("alice@example.com");
        assertThat(tenantAMessage.getBouncedRecipients()).isEmpty();
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void unknownClassificationPersistsWithoutMutation() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        DsnIngestionResult result = service.ingest(new DsnIngestionRequest(
                DsnFixtures.rfc3464("delivered", "2.0.0", "smtp; 250 ok", "alice@example.com", "<abc@texto.local>", false, false, null),
                bounceAddress(tenantAMessage)
        ));

        assertThat(result.outcome()).isEqualTo(DsnIngestionResult.Outcome.ACCEPTED);
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(tenantAMessage.getBouncedRecipients()).isEmpty();
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void policyRejectionBouncesWithoutSuppression() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        DsnIngestionResult result = service.ingest(new DsnIngestionRequest(
                DsnFixtures.policyRejection("<abc@texto.local>", "alice@example.com"),
                bounceAddress(tenantAMessage)
        ));

        assertThat(result.outcome()).isEqualTo(DsnIngestionResult.Outcome.ACCEPTED);
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_BOUNCED);
        assertThat(tenantAMessage.getBouncedRecipients()).contains("alice@example.com");
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void aliceBounceDoesNotAffectBob() {
        EmailMessageEntity multi = delivered(tenantA, "<multi@texto.local>", List.of("alice@example.com", "bob@example.com"));
        when(emailMessageRepository.findByBounceCorrelationToken(multi.getBounceCorrelationToken()))
                .thenReturn(Optional.of(multi));

        service.ingest(new DsnIngestionRequest(
                DsnFixtures.hardBounce("<multi@texto.local>", "alice@example.com"),
                bounceAddress(multi)
        ));

        assertThat(multi.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(multi.getBouncedRecipients()).containsExactly("alice@example.com");
        assertThat(multi.getBouncedRecipients()).doesNotContain("bob@example.com");
        verify(suppressionService).recordBounce(eq(tenantA), eq("alice@example.com"), eq(multi.getId()), any());
        verify(suppressionService, never()).recordBounce(eq(tenantA), eq("bob@example.com"), any(), any());
    }

    @Test
    void tenantBTokenCannotMutateTenantA() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantBMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantBMessage));

        service.ingest(new DsnIngestionRequest(
                DsnFixtures.hardBounce("<abc@texto.local>", "alice@example.com"),
                bounceAddress(tenantBMessage)
        ));

        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(tenantAMessage.getBouncedRecipients()).isEmpty();
        assertThat(tenantBMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_BOUNCED);
        verify(suppressionService, never()).recordBounce(eq(tenantA), any(), any(), any());
        verify(emailMessageRepository, never()).findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken());
    }

    @Test
    void uncorrelatedDoesNotSuppress() {
        String unknown = "ffffffffffffffffffffffffffffffff";
        when(emailMessageRepository.findByBounceCorrelationToken(unknown)).thenReturn(Optional.empty());

        DsnIngestionResult result = service.ingest(new DsnIngestionRequest(
                DsnFixtures.hardBounce("<abc@texto.local>", "alice@example.com"),
                BounceAddress.format(unknown, "bounce.texto.test")
        ));

        assertThat(result.outcome()).isEqualTo(DsnIngestionResult.Outcome.UNMATCHED);
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
        verify(emailMessageRepository, never()).save(any());
    }

    @Test
    void duplicateEventDoesNotReapplyPolicy() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        byte[] raw = DsnFixtures.hardBounce("<abc@texto.local>", "alice@example.com");
        DsnIngestionResult first = service.ingest(new DsnIngestionRequest(raw, bounceAddress(tenantAMessage)));
        ArgumentCaptor<BounceEventEntity> captor = ArgumentCaptor.forClass(BounceEventEntity.class);
        verify(bounceEventRepository).save(captor.capture());
        when(bounceEventRepository.findByEventHash(any())).thenReturn(Optional.of(captor.getValue()));

        DsnIngestionResult second = service.ingest(new DsnIngestionRequest(raw, bounceAddress(tenantAMessage)));
        assertThat(first.outcome()).isEqualTo(DsnIngestionResult.Outcome.ACCEPTED);
        assertThat(second.outcome()).isEqualTo(DsnIngestionResult.Outcome.DUPLICATE);
        verify(suppressionService, times(1)).recordBounce(any(), any(), any(), any());
        verify(bounceEventRepository, times(1)).save(any());
    }

    @Test
    void messageIdFallbackDoesNotMutateOrSuppress() {
        when(emailMessageRepository.findByRfc822MessageId("<abc@texto.local>"))
                .thenReturn(List.of(tenantAMessage));

        DsnIngestionResult result = service.ingest(DsnIngestionRequest.of(
                DsnFixtures.hardBounce("<abc@texto.local>", "alice@example.com")
        ));

        assertThat(result.outcome()).isEqualTo(DsnIngestionResult.Outcome.ACCEPTED);
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void malformedInputDoesNotThrow() {
        assertThatCode(() -> {
            DsnIngestionResult result = service.ingest(DsnIngestionRequest.of("garbage".getBytes()));
            assertThat(result.outcome()).isEqualTo(DsnIngestionResult.Outcome.PARSE_FAILED);
        }).doesNotThrowAnyException();
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void nullRequestIsRejected() {
        DsnIngestionResult result = service.ingest(null);
        assertThat(result.outcome()).isEqualTo(DsnIngestionResult.Outcome.REJECTED);
        verify(bounceEventRepository, never()).save(any());
    }

    private static EmailMessageEntity delivered(UUID tenantId, String rfc822MessageId, List<String> to) {
        EmailMessageEntity entity = EmailMessageEntity.create(
                tenantId,
                "noreply@acme.texto.test",
                to,
                List.of(),
                List.of(),
                List.of(),
                null,
                "Hello",
                null,
                "hello",
                null,
                null,
                null,
                Map.of(),
                5,
                null
        );
        entity.recordRfc822MessageId(rfc822MessageId);
        entity.markProcessing();
        entity.markSending();
        entity.markDelivered("qid", "250 queued as qid");
        return entity;
    }

    private static String bounceAddress(EmailMessageEntity message) {
        return BounceAddress.format(message.getBounceCorrelationToken(), "bounce.texto.test");
    }
}
