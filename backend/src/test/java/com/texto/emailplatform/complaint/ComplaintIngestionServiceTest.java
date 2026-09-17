package com.texto.emailplatform.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.bounce.BounceTokenExtractor;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.complaint.domain.ComplaintEventEntity;
import com.texto.emailplatform.complaint.domain.ComplaintEventRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import com.texto.emailplatform.webhook.WebhookEventTypes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComplaintIngestionServiceTest {

    @Mock
    private ComplaintEventRepository complaintEventRepository;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private SuppressionService suppressionService;

    @Mock
    private WebhookEventPublisher webhookEventPublisher;

    private ComplaintIngestionService service;
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
        ComplaintMetrics metrics = new ComplaintMetrics(new SimpleMeterRegistry());
        ComplaintApplicationService application = new ComplaintApplicationService(
                suppressionService,
                webhookEventPublisher,
                metrics
        );
        service = new ComplaintIngestionService(
                new ComplaintCorrelator(emailMessageRepository, new BounceTokenExtractor(new EmailPlatformProperties())),
                complaintEventRepository,
                application,
                metrics,
                new EmailPlatformProperties()
        );
        org.mockito.Mockito.lenient().when(complaintEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(complaintEventRepository.findByEventHash(any())).thenReturn(Optional.empty());
    }

    @Test
    void validComplaintSuppressesWithoutChangingDeliveryStatus() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        ComplaintIngestionResult result = service.ingest(tokenRequest(tenantAMessage, "alice@example.com", "ABUSE", "evt-1"));

        assertThat(result.outcome()).isEqualTo(ComplaintIngestionResult.Outcome.ACCEPTED);
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(result.event().getTenantId()).isEqualTo(tenantA);
        assertThat(result.event().getProcessingStatus()).isEqualTo(ComplaintEventEntity.PROCESSING_APPLIED);
        verify(suppressionService).recordComplaint(
                tenantA,
                "alice@example.com",
                tenantAMessage.getId(),
                ComplaintApplicationService.REASON_COMPLAINT
        );
        verify(webhookEventPublisher).publishEmailEvent(WebhookEventTypes.EMAIL_COMPLAINT, tenantAMessage);
    }

    @Test
    void recipientNormalizationMatchesStoredCasing() {
        tenantAMessage = delivered(tenantA, "<abc@texto.local>", List.of("Alice@Example.com"));
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        service.ingest(tokenRequest(tenantAMessage, "  ALICE@example.com  ", "SPAM", null));

        verify(suppressionService).recordComplaint(
                eq(tenantA),
                eq("Alice@Example.com"),
                eq(tenantAMessage.getId()),
                eq(ComplaintApplicationService.REASON_COMPLAINT)
        );
    }

    @Test
    void unknownCategoryStillSuppressesWhenCorrelated() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        ComplaintIngestionResult result = service.ingest(tokenRequest(tenantAMessage, "alice@example.com", "not-a-type", "evt-u"));

        assertThat(result.event().getComplaintType()).isEqualTo("UNKNOWN");
        verify(suppressionService).recordComplaint(any(), any(), any(), any());
    }

    @Test
    void aliceComplaintDoesNotAffectBob() {
        tenantAMessage = delivered(tenantA, "<abc@texto.local>", List.of("alice@example.com", "bob@example.com"));
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));

        service.ingest(tokenRequest(tenantAMessage, "alice@example.com", "ABUSE", "evt-alice"));

        verify(suppressionService).recordComplaint(tenantA, "alice@example.com", tenantAMessage.getId(), ComplaintApplicationService.REASON_COMPLAINT);
        verify(suppressionService, never()).recordComplaint(eq(tenantA), eq("bob@example.com"), any(), any());
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
    }

    @Test
    void tenantBTokenCannotSuppressTenantA() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantBMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantBMessage));

        service.ingest(tokenRequest(tenantBMessage, "alice@example.com", "ABUSE", "evt-b"));

        verify(suppressionService).recordComplaint(tenantB, "alice@example.com", tenantBMessage.getId(), ComplaintApplicationService.REASON_COMPLAINT);
        verify(suppressionService, never()).recordComplaint(eq(tenantA), any(), any(), any());
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
    }

    @Test
    void uncorrelatedDoesNotSuppress() {
        ComplaintIngestionResult result = service.ingest(new ComplaintIngestionRequest(
                "TEST", "evt-x", "<missing@texto.local>", null, "alice@example.com", null, "ABUSE", null, Map.of()
        ));

        assertThat(result.outcome()).isEqualTo(ComplaintIngestionResult.Outcome.UNCORRELATED);
        verify(suppressionService, never()).recordComplaint(any(), any(), any(), any());
    }

    @Test
    void duplicateProviderEventIdSkipsPolicy() {
        ComplaintIngestionRequest request = tokenRequest(tenantAMessage, "alice@example.com", "ABUSE", "evt-dup");
        ComplaintEventEntity stored = ComplaintEventEntity.create(
                tenantA, tenantAMessage.getId(), ComplaintEventHash.identity(request),
                ComplaintEventEntity.CORRELATION_MATCHED, ComplaintEventEntity.PROCESSING_APPLIED,
                "INTERNAL", "evt-dup", "ABUSE", "alice@example.com", "alice@example.com",
                null, null, "abcd1234", null, null, Map.of()
        );
        when(complaintEventRepository.findByEventHash(ComplaintEventHash.identity(request))).thenReturn(Optional.of(stored));

        ComplaintIngestionResult result = service.ingest(request);

        assertThat(result.outcome()).isEqualTo(ComplaintIngestionResult.Outcome.DUPLICATE);
        verify(suppressionService, never()).recordComplaint(any(), any(), any(), any());
        verify(complaintEventRepository, never()).save(any());
    }

    @Test
    void hashFallbackIdempotencyWithoutProviderEventId() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));
        ComplaintIngestionRequest request = tokenRequest(tenantAMessage, "alice@example.com", "ABUSE", null);
        when(complaintEventRepository.findByEventHash(ComplaintEventHash.identity(request)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ComplaintEventEntity.create(
                        tenantA, tenantAMessage.getId(), "hash",
                        ComplaintEventEntity.CORRELATION_MATCHED, ComplaintEventEntity.PROCESSING_APPLIED,
                        "INTERNAL", null, "ABUSE", "alice@example.com", "alice@example.com",
                        null, null, null, null, null, Map.of()
                )));

        service.ingest(request);
        ComplaintIngestionResult second = service.ingest(request);

        assertThat(second.outcome()).isEqualTo(ComplaintIngestionResult.Outcome.DUPLICATE);
        verify(suppressionService, times(1)).recordComplaint(any(), any(), any(), any());
    }

    @Test
    void messageIdFallbackDoesNotSuppress() {
        when(emailMessageRepository.findByRfc822MessageId("<abc@texto.local>"))
                .thenReturn(List.of(tenantAMessage));

        ComplaintIngestionResult result = service.ingest(new ComplaintIngestionRequest(
                "TEST", "evt-mid", "<abc@texto.local>", null, "alice@example.com", null, "ABUSE", null, Map.of()
        ));

        assertThat(result.outcome()).isEqualTo(ComplaintIngestionResult.Outcome.ACCEPTED);
        assertThat(result.event().getProcessingStatus()).isEqualTo(ComplaintEventEntity.PROCESSING_NO_ACTION);
        assertThat(tenantAMessage.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        verify(suppressionService, never()).recordComplaint(any(), any(), any(), any());
    }

    @Test
    void webhookFailureDoesNotPreventSuppression() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));
        org.mockito.Mockito.doThrow(new RuntimeException("webhook down"))
                .when(webhookEventPublisher)
                .publishEmailEvent(any(), any());

        ComplaintIngestionResult result = service.ingest(tokenRequest(tenantAMessage, "alice@example.com", "ABUSE", "evt-wh"));

        assertThat(result.outcome()).isEqualTo(ComplaintIngestionResult.Outcome.ACCEPTED);
        verify(suppressionService).recordComplaint(any(), any(), any(), any());
    }

    @Test
    void malformedPersistDoesNotSuppress() {
        ComplaintIngestionResult result = service.ingestMalformed("{".getBytes(), "malformed_json");
        assertThat(result.outcome()).isEqualTo(ComplaintIngestionResult.Outcome.PARSE_FAILED);
        verify(suppressionService, never()).recordComplaint(any(), any(), any(), any());
    }

    @Test
    void nullRequestIsRejected() {
        assertThat(service.ingest(null).outcome()).isEqualTo(ComplaintIngestionResult.Outcome.REJECTED);
        verify(complaintEventRepository, never()).save(any());
    }

    @Test
    void metadataSecretsAreDropped() {
        when(emailMessageRepository.findByBounceCorrelationToken(tenantAMessage.getBounceCorrelationToken()))
                .thenReturn(Optional.of(tenantAMessage));
        ComplaintIngestionResult result = service.ingest(new ComplaintIngestionRequest(
                "INTERNAL",
                "evt-meta",
                null,
                null,
                "alice@example.com",
                tenantAMessage.getBounceCorrelationToken(),
                "ABUSE",
                null,
                Map.of("token", "secret-value", "note", "ok")
        ));
        assertThat(result.event().getMetadata()).containsEntry("note", "ok").doesNotContainKey("token");
    }

    private static ComplaintIngestionRequest tokenRequest(
            EmailMessageEntity message,
            String recipient,
            String type,
            String providerEventId
    ) {
        return new ComplaintIngestionRequest(
                "INTERNAL",
                providerEventId,
                message.getRfc822MessageId(),
                null,
                recipient,
                message.getBounceCorrelationToken(),
                type,
                null,
                Map.of()
        );
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
}
