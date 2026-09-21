package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.bounce.BounceTokenExtractor;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.complaint.ComplaintApplicationService;
import com.texto.emailplatform.complaint.ComplaintIngestionRequest;
import com.texto.emailplatform.complaint.ComplaintIngestionService;
import com.texto.emailplatform.complaint.ComplaintIngestionResult;
import com.texto.emailplatform.complaint.ComplaintMetrics;
import com.texto.emailplatform.complaint.ComplaintCorrelator;
import com.texto.emailplatform.complaint.domain.ComplaintEventEntity;
import com.texto.emailplatform.complaint.domain.ComplaintEventRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesComplaintIngestionServiceTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;
    @Mock
    private ComplaintEventRepository complaintEventRepository;
    @Mock
    private SuppressionService suppressionService;
    @Mock
    private WebhookEventPublisher webhookEventPublisher;

    private SesComplaintIngestionService service;
    private UUID tenantId;
    private EmailMessageEntity message;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        message = delivered(List.of("a@example.com", "b@example.com"), List.of("c@example.com"), List.of("d@example.com"));
        ComplaintMetrics metrics = new ComplaintMetrics(new SimpleMeterRegistry());
        ComplaintIngestionService ingestion = new ComplaintIngestionService(
                new ComplaintCorrelator(emailMessageRepository, new BounceTokenExtractor(new EmailPlatformProperties())),
                complaintEventRepository,
                new ComplaintApplicationService(suppressionService, webhookEventPublisher, metrics),
                metrics,
                new EmailPlatformProperties()
        );
        service = new SesComplaintIngestionService(ingestion, emailMessageRepository);
        org.mockito.Mockito.lenient().when(complaintEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(complaintEventRepository.findByEventHash(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(complaintEventRepository.findByProviderAndProviderEventIdAndRecipient(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void singleComplaintSuppressesRecipient() {
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));

        service.ingest(complaint(List.of("a@example.com")));

        verify(suppressionService).recordComplaint(tenantId, "a@example.com", message.getId(), ComplaintApplicationService.REASON_COMPLAINT);
        assertThat(message.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        ArgumentCaptor<ComplaintEventEntity> captor = ArgumentCaptor.forClass(ComplaintEventEntity.class);
        verify(complaintEventRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("AWS_SES");
        assertThat(captor.getValue().getProviderEventId()).isEqualTo("feedback-id");
        assertThat(captor.getValue().getProviderMessageId()).isEqualTo("mid-1");
    }

    @Test
    void multipleComplainedRecipientsAreProcessed() {
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));

        service.ingest(complaint(List.of("b@example.com", "d@example.com")));

        verify(suppressionService).recordComplaint(tenantId, "b@example.com", message.getId(), ComplaintApplicationService.REASON_COMPLAINT);
        verify(suppressionService).recordComplaint(tenantId, "d@example.com", message.getId(), ComplaintApplicationService.REASON_COMPLAINT);
        verify(complaintEventRepository, times(2)).save(any());
    }

    @Test
    void duplicateComplaintDoesNotResuppress() {
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));
        when(complaintEventRepository.findByEventHash(any())).thenReturn(Optional.of(ComplaintEventEntity.create(
                tenantId, message.getId(), "hash", "MATCHED", "APPLIED",
                "AWS_SES", "feedback-id", "ABUSE", "a@example.com", "a@example.com",
                null, "mid-1", null, null, null, Map.of()
        )));

        service.ingest(complaint(List.of("a@example.com")));

        verify(suppressionService, never()).recordComplaint(any(), any(), any(), any());
        verify(complaintEventRepository, never()).save(any());
    }

    @Test
    void persistenceFailurePropagates() {
        ComplaintIngestionService failing = org.mockito.Mockito.mock(ComplaintIngestionService.class);
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));
        SesComplaintIngestionService isolated = new SesComplaintIngestionService(failing, emailMessageRepository);
        when(failing.ingestFromTrustedSes(any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> isolated.ingest(complaint(List.of("a@example.com"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
    }

    @Test
    void unauthorizedProviderPathDoesNotMutateWhenUsingUntrustedIngest() {
        ComplaintMetrics metrics = new ComplaintMetrics(new SimpleMeterRegistry());
        ComplaintIngestionService ingestion = new ComplaintIngestionService(
                new ComplaintCorrelator(emailMessageRepository, new BounceTokenExtractor(new EmailPlatformProperties())),
                complaintEventRepository,
                new ComplaintApplicationService(suppressionService, webhookEventPublisher, metrics),
                metrics,
                new EmailPlatformProperties()
        );
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));

        ComplaintIngestionResult result = ingestion.ingest(new ComplaintIngestionRequest(
                "AWS_SES",
                "feedback-id",
                null,
                "mid-1",
                "a@example.com",
                null,
                "ABUSE",
                Instant.now(),
                Map.of()
        ));

        assertThat(result.event().getProcessingStatus()).isEqualTo(ComplaintEventEntity.PROCESSING_NO_ACTION);
        verify(suppressionService, never()).recordComplaint(any(), any(), any(), any());
    }

    @Test
    void missingProviderMessageIdIsUnprocessable() {
        assertThatThrownBy(() -> service.ingest(complaint(null, List.of("a@example.com"))))
                .isInstanceOf(UnprocessableSesFeedbackException.class);
    }

    @Test
    void unmatchedProviderMessageIdThrowsWithoutPersisting() {
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.ingest(complaint(List.of("a@example.com"))))
                .isInstanceOf(UnresolvedSesFeedbackException.class)
                .hasMessage("unmatched_provider_message_id");
        verify(complaintEventRepository, never()).save(any());
        verify(suppressionService, never()).recordComplaint(any(), any(), any(), any());
    }

    @Test
    void ambiguousProviderMessageIdThrowsWithoutPersisting() {
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message, delivered(
                List.of("a@example.com"), List.of(), List.of()
        )));

        assertThatThrownBy(() -> service.ingest(complaint(List.of("a@example.com"))))
                .isInstanceOf(UnresolvedSesFeedbackException.class)
                .hasMessage("ambiguous_provider_message_id");
        verify(complaintEventRepository, never()).save(any());
        verify(suppressionService, never()).recordComplaint(any(), any(), any(), any());
    }

    private EmailMessageEntity delivered(List<String> to, List<String> cc, List<String> bcc) {
        EmailMessageEntity entity = EmailMessageEntity.create(
                tenantId,
                "noreply@acme.texto.test",
                to,
                cc,
                bcc,
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
        entity.markProcessing();
        entity.markSending();
        entity.markDelivered("mid-1", "250 SES accepted");
        return entity;
    }

    private static SesFeedbackEvent complaint(List<String> recipients) {
        return complaint("mid-1", recipients);
    }

    private static SesFeedbackEvent complaint(String messageId, List<String> recipients) {
        return new SesFeedbackEvent(
                "Complaint",
                messageId,
                List.of(),
                "sender@example.com",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "feedback-id",
                null,
                "abuse",
                recipients.stream().map(SesFeedbackEvent.ComplainedRecipient::new).toList(),
                Map.of()
        );
    }
}
