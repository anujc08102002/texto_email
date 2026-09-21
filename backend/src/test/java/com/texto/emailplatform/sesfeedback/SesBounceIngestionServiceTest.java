package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.bounce.BounceApplicationService;
import com.texto.emailplatform.bounce.BounceMetrics;
import com.texto.emailplatform.bounce.domain.BounceEventEntity;
import com.texto.emailplatform.bounce.domain.BounceEventRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SesBounceIngestionServiceTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private BounceEventRepository bounceEventRepository;

    @Mock
    private BounceApplicationService bounceApplicationService;

    @Mock
    private BounceMetrics bounceMetrics;

    private SesBounceIngestionService service;

    @BeforeEach
    void setUp() {
        service = new SesBounceIngestionService(
                emailMessageRepository,
                bounceEventRepository,
                bounceApplicationService,
                bounceMetrics
        );
    }

    @Test
    void ingest_ValidBounceEvent_ProcessesSuccessfully() {
        // Arrange
        String providerMessageId = "EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000";
        EmailMessageEntity message = createTestMessage();
        
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Bounce",
                providerMessageId,
                List.of("recipient@example.com"),
                "sender@example.com",
                Instant.now(),
                "Permanent",
                "General",
                "failed",
                "5.1.1",
                "550 5.1.1 User unknown",
                "dns; smtp.example.com",
                "192.0.2.1",
                "feedback-id",
                List.of(new SesFeedbackEvent.BouncedRecipient("recipient@example.com", "failed", "5.1.1", "550 5.1.1 User unknown")),
                null,
                null,
                null
        );

        when(emailMessageRepository.findByProviderMessageId(providerMessageId))
                .thenReturn(List.of(message));
        when(bounceEventRepository.findByEventHash(any())).thenReturn(Optional.empty());

        // Act
        service.ingest(event);

        // Assert
        verify(bounceEventRepository, times(1)).save(any(BounceEventEntity.class));
        verify(bounceApplicationService, times(1)).apply(eq(message), eq("recipient@example.com"), any(), eq(false));
        verify(bounceMetrics, times(1)).incrementEvent();
    }

    @Test
    void ingest_DuplicateEvent_IgnoresDuplicate() {
        // Arrange
        String providerMessageId = "EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000";
        EmailMessageEntity message = createTestMessage();
        BounceEventEntity existingEvent = new BounceEventEntity();
        
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Bounce",
                providerMessageId,
                List.of("recipient@example.com"),
                "sender@example.com",
                Instant.now(),
                "Permanent",
                "General",
                "failed",
                "5.1.1",
                "550 5.1.1 User unknown",
                "dns; smtp.example.com",
                "192.0.2.1",
                "feedback-id",
                List.of(new SesFeedbackEvent.BouncedRecipient("recipient@example.com", "failed", "5.1.1", "550 5.1.1 User unknown")),
                null,
                null,
                null
        );

        when(emailMessageRepository.findByProviderMessageId(providerMessageId))
                .thenReturn(List.of(message));
        when(bounceEventRepository.findByEventHash(any())).thenReturn(Optional.of(existingEvent));

        // Act
        service.ingest(event);

        // Assert
        verify(bounceEventRepository, never()).save(any());
        verify(bounceApplicationService, never()).apply(any(), any(), any(), any());
        verify(bounceMetrics).incrementDuplicate();
    }

    @Test
    void ingest_NoMatchingMessage_DoesNotProcess() {
        // Arrange
        String providerMessageId = "EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000";
        
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Bounce",
                providerMessageId,
                List.of("recipient@example.com"),
                "sender@example.com",
                Instant.now(),
                "Permanent",
                "General",
                "failed",
                "5.1.1",
                "550 5.1.1 User unknown",
                "dns; smtp.example.com",
                "192.0.2.1",
                "feedback-id",
                List.of(new SesFeedbackEvent.BouncedRecipient("recipient@example.com", "failed", "5.1.1", "550 5.1.1 User unknown")),
                null,
                null,
                null
        );

        when(emailMessageRepository.findByProviderMessageId(providerMessageId))
                .thenReturn(List.of());

        // Act
        service.ingest(event);

        // Assert
        verify(bounceEventRepository, never()).save(any());
        verify(bounceApplicationService, never()).apply(any(), any(), any(), any());
    }

    @Test
    void ingest_MissingProviderMessageId_DoesNotProcess() {
        // Arrange
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Bounce",
                null,
                List.of("recipient@example.com"),
                "sender@example.com",
                Instant.now(),
                "Permanent",
                "General",
                "failed",
                "5.1.1",
                "550 5.1.1 User unknown",
                "dns; smtp.example.com",
                "192.0.2.1",
                "feedback-id",
                List.of(new SesFeedbackEvent.BouncedRecipient("recipient@example.com", "failed", "5.1.1", "550 5.1.1 User unknown")),
                null,
                null,
                null
        );

        // Act
        service.ingest(event);

        // Assert
        verify(emailMessageRepository, never()).findByProviderMessageId(any());
        verify(bounceEventRepository, never()).save(any());
    }

    @Test
    void ingest_NullEvent_DoesNotProcess() {
        // Act
        service.ingest(null);

        // Assert
        verify(emailMessageRepository, never()).findByProviderMessageId(any());
        verify(bounceEventRepository, never()).save(any());
    }

    @Test
    void ingest_ComplaintEvent_DoesNotProcess() {
        // Arrange
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Complaint",
                "EXAMPLE",
                List.of("recipient@example.com"),
                "sender@example.com",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "abuse",
                List.of(new SesFeedbackEvent.ComplainedRecipient("recipient@example.com")),
                null
        );

        // Act
        service.ingest(event);

        // Assert
        verify(emailMessageRepository, never()).findByProviderMessageId(any());
        verify(bounceEventRepository, never()).save(any());
    }

    private EmailMessageEntity createTestMessage() {
        EmailMessageEntity message = new EmailMessageEntity();
        message.setId(UUID.randomUUID());
        message.setTenantId(UUID.randomUUID());
        message.setRecipient("recipient@example.com");
        message.setProviderMessageId("EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000");
        return message;
    }
}
