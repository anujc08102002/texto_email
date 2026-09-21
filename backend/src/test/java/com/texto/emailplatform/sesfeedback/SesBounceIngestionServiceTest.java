package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.bounce.BounceApplicationService;
import com.texto.emailplatform.bounce.BounceMetrics;
import com.texto.emailplatform.bounce.domain.BounceEventEntity;
import com.texto.emailplatform.bounce.domain.BounceEventRepository;
import com.texto.emailplatform.common.FeedbackAuthorization;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesBounceIngestionServiceTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;
    @Mock
    private BounceEventRepository bounceEventRepository;
    @Mock
    private SuppressionService suppressionService;
    @Mock
    private WebhookEventPublisher webhookEventPublisher;

    private SesBounceIngestionService service;
    private BounceApplicationService bounceApplicationService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        BounceMetrics metrics = new BounceMetrics(new SimpleMeterRegistry());
        bounceApplicationService = new BounceApplicationService(
                emailMessageRepository,
                suppressionService,
                webhookEventPublisher,
                metrics
        );
        service = new SesBounceIngestionService(
                emailMessageRepository,
                bounceEventRepository,
                bounceApplicationService,
                metrics
        );
        org.mockito.Mockito.lenient().when(bounceEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(bounceEventRepository.findByEventHash(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(bounceEventRepository.findByProviderAndProviderEventIdAndFinalRecipient(any(), any(), any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(emailMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void singleRecipientHardBounceMutatesAndSuppresses() {
        EmailMessageEntity message = delivered(List.of("a@example.com"), List.of(), List.of());
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));

        service.ingest(bounce("Permanent", "General", List.of(bounced("a@example.com"))));

        assertThat(message.getStatus()).isEqualTo(EmailMessageEntity.STATUS_BOUNCED);
        assertThat(message.getBouncedRecipients()).contains("a@example.com");
        verify(suppressionService).recordBounce(eq(tenantId), eq("a@example.com"), eq(message.getId()), any());
        ArgumentCaptor<BounceEventEntity> captor = ArgumentCaptor.forClass(BounceEventEntity.class);
        verify(bounceEventRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("AWS_SES");
        assertThat(captor.getValue().getProviderEventId()).isEqualTo("feedback-id");
    }

    @Test
    void multipleBouncedRecipientsAreProcessedIndependently() {
        EmailMessageEntity message = delivered(
                List.of("a@example.com", "b@example.com"),
                List.of("c@example.com"),
                List.of("d@example.com")
        );
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));

        service.ingest(bounce("Permanent", "General", List.of(
                bounced("b@example.com"),
                bounced("c@example.com"),
                bounced("d@example.com")
        )));

        assertThat(message.getBouncedRecipients()).containsExactlyInAnyOrder("b@example.com", "c@example.com", "d@example.com");
        assertThat(message.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        verify(suppressionService).recordBounce(eq(tenantId), eq("b@example.com"), eq(message.getId()), any());
        verify(suppressionService).recordBounce(eq(tenantId), eq("c@example.com"), eq(message.getId()), any());
        verify(suppressionService).recordBounce(eq(tenantId), eq("d@example.com"), eq(message.getId()), any());
        verify(bounceEventRepository, times(3)).save(any());
    }

    @Test
    void unknownRecipientDoesNotMutate() {
        EmailMessageEntity message = delivered(List.of("a@example.com"), List.of(), List.of());
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));

        service.ingest(bounce("Permanent", "General", List.of(bounced("other@example.com"))));

        assertThat(message.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(message.getBouncedRecipients()).isEmpty();
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
        verify(bounceEventRepository).save(any());
    }

    @Test
    void unmatchedProviderMessageIdThrows() {
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.ingest(bounce("Permanent", "General", List.of(bounced("a@example.com")))))
                .isInstanceOf(UnresolvedSesFeedbackException.class)
                .hasMessage("unmatched_provider_message_id");
        verify(bounceEventRepository, never()).save(any());
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void ambiguousProviderMessageIdThrows() {
        EmailMessageEntity one = delivered(List.of("a@example.com"), List.of(), List.of());
        EmailMessageEntity two = delivered(List.of("a@example.com"), List.of(), List.of());
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(one, two));

        assertThatThrownBy(() -> service.ingest(bounce("Permanent", "General", List.of(bounced("a@example.com")))))
                .isInstanceOf(UnresolvedSesFeedbackException.class)
                .hasMessage("ambiguous_provider_message_id");
        verify(bounceEventRepository, never()).save(any());
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void duplicateEventDoesNotReapply() {
        EmailMessageEntity message = delivered(List.of("a@example.com"), List.of(), List.of());
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));
        when(bounceEventRepository.findByEventHash(any())).thenReturn(Optional.of(new BounceEventEntity()));

        service.ingest(bounce("Permanent", "General", List.of(bounced("a@example.com"))));

        verify(bounceEventRepository, never()).save(any());
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void softBounceRecordsSoftRecipientWithoutSuppression() {
        EmailMessageEntity message = delivered(List.of("a@example.com"), List.of(), List.of());
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));

        service.ingest(bounce("Transient", "MailboxFull", List.of(bounced("a@example.com"))));

        assertThat(message.getStatus()).isEqualTo(EmailMessageEntity.STATUS_DELIVERED);
        assertThat(message.getSoftBouncedRecipients()).contains("a@example.com");
        verify(suppressionService, never()).recordBounce(any(), any(), any(), any());
    }

    @Test
    void missingProviderMessageIdIsUnprocessable() {
        SesFeedbackEvent event = bounce(null, "Permanent", "General", List.of(bounced("a@example.com")));
        assertThatThrownBy(() -> service.ingest(event)).isInstanceOf(UnprocessableSesFeedbackException.class);
        verify(bounceEventRepository, never()).save(any());
    }

    @Test
    void sesAuthorizationIsUsedNotToken() {
        BounceApplicationService mockedApply = org.mockito.Mockito.mock(BounceApplicationService.class);
        SesBounceIngestionService isolated = new SesBounceIngestionService(
                emailMessageRepository,
                bounceEventRepository,
                mockedApply,
                new BounceMetrics(new SimpleMeterRegistry())
        );
        EmailMessageEntity message = delivered(List.of("a@example.com"), List.of(), List.of());
        when(emailMessageRepository.findByProviderMessageId("mid-1")).thenReturn(List.of(message));

        isolated.ingest(bounce("Permanent", "General", List.of(bounced("a@example.com"))));

        verify(mockedApply).apply(eq(message), eq("a@example.com"), any(), eq(FeedbackAuthorization.SES));
        verify(mockedApply, never()).apply(any(), any(), any(), eq(true));
        verify(mockedApply, never()).apply(any(), any(), any(), eq(false));
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

    private static SesFeedbackEvent bounce(
            String bounceType,
            String subType,
            List<SesFeedbackEvent.BouncedRecipient> recipients
    ) {
        return bounce("mid-1", bounceType, subType, recipients);
    }

    private static SesFeedbackEvent bounce(
            String messageId,
            String bounceType,
            String subType,
            List<SesFeedbackEvent.BouncedRecipient> recipients
    ) {
        return new SesFeedbackEvent(
                "Bounce",
                messageId,
                List.of(),
                "sender@example.com",
                Instant.parse("2024-01-15T10:30:00Z"),
                bounceType,
                subType,
                "failed",
                "5.1.1",
                "550 5.1.1 User unknown",
                "dns; smtp.example.com",
                "192.0.2.1",
                "feedback-id",
                recipients,
                null,
                null,
                Map.of()
        );
    }

    private static SesFeedbackEvent.BouncedRecipient bounced(String email) {
        return new SesFeedbackEvent.BouncedRecipient(email, "failed", "5.1.1", "550 5.1.1");
    }
}
