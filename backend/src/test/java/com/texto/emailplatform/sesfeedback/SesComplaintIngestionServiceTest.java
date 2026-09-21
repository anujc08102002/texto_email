package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.complaint.ComplaintIngestionRequest;
import com.texto.emailplatform.complaint.ComplaintIngestionResult;
import com.texto.emailplatform.complaint.ComplaintIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SesComplaintIngestionServiceTest {

    @Mock
    private ComplaintIngestionService complaintIngestionService;

    private SesComplaintIngestionService service;

    @BeforeEach
    void setUp() {
        service = new SesComplaintIngestionService(complaintIngestionService);
    }

    @Test
    void ingest_ValidComplaintEvent_ProcessesSuccessfully() {
        // Arrange
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Complaint",
                "EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000",
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
                "feedback-id",
                "abuse",
                List.of(new SesFeedbackEvent.ComplainedRecipient("recipient@example.com")),
                Map.of("complaintFeedbackType", "abuse")
        );

        when(complaintIngestionService.ingest(any(ComplaintIngestionRequest.class)))
                .thenReturn(ComplaintIngestionResult.accepted(null));

        // Act
        service.ingest(event);

        // Assert
        ArgumentCaptor<ComplaintIngestionRequest> captor = ArgumentCaptor.forClass(ComplaintIngestionRequest.class);
        verify(complaintIngestionService).ingest(captor.capture());
        
        ComplaintIngestionRequest request = captor.getValue();
        assertEquals("AWS_SES", request.provider());
        assertEquals("feedback-id", request.providerEventId());
        assertEquals("EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000", request.providerMessageId());
        assertEquals("recipient@example.com", request.recipient());
        assertEquals("ABUSE", request.complaintType());
        assertNotNull(request.occurredAt());
        assertTrue(request.metadata().containsKey("source"));
    }

    @Test
    void ingest_MapsComplaintTypeCorrectly() {
        // Test complaint type mapping
        String[] sesTypes = {"abuse", "spam", "fraud", "virus", "other"};
        
        for (String sesType : sesTypes) {
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
                    "feedback-id",
                    sesType,
                    List.of(new SesFeedbackEvent.ComplainedRecipient("recipient@example.com")),
                    Map.of()
            );

            when(complaintIngestionService.ingest(any(ComplaintIngestionRequest.class)))
                    .thenReturn(ComplaintIngestionResult.accepted(null));

            service.ingest(event);

            ArgumentCaptor<ComplaintIngestionRequest> captor = ArgumentCaptor.forClass(ComplaintIngestionRequest.class);
            verify(complaintIngestionService, atLeastOnce()).ingest(captor.capture());
            
            String expectedType = switch (sesType.toLowerCase()) {
                case "virus" -> "ABUSE";
                case "other" -> "UNKNOWN";
                default -> sesType.toUpperCase();
            };
            assertEquals(expectedType, captor.getValue().complaintType());
        }
    }

    @Test
    void ingest_MissingProviderMessageId_DoesNotProcess() {
        // Arrange
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Complaint",
                null,
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
                "feedback-id",
                "abuse",
                List.of(new SesFeedbackEvent.ComplainedRecipient("recipient@example.com")),
                Map.of()
        );

        // Act
        service.ingest(event);

        // Assert
        verify(complaintIngestionService, never()).ingest(any());
    }

    @Test
    void ingest_MissingRecipients_DoesNotProcess() {
        // Arrange
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Complaint",
                "EXAMPLE",
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
                "abuse",
                List.of(),
                Map.of()
        );

        // Act
        service.ingest(event);

        // Assert
        verify(complaintIngestionService, never()).ingest(any());
    }

    @Test
    void ingest_NullEvent_DoesNotProcess() {
        // Act
        service.ingest(null);

        // Assert
        verify(complaintIngestionService, never()).ingest(any());
    }

    @Test
    void ingest_BounceEvent_DoesNotProcess() {
        // Arrange
        SesFeedbackEvent event = new SesFeedbackEvent(
                "Bounce",
                "EXAMPLE",
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
        verify(complaintIngestionService, never()).ingest(any());
    }

    @Test
    void ingest_IngestionFailure_LogsError() {
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
                "feedback-id",
                "abuse",
                List.of(new SesFeedbackEvent.ComplainedRecipient("recipient@example.com")),
                Map.of()
        );

        when(complaintIngestionService.ingest(any(ComplaintIngestionRequest.class)))
                .thenThrow(new RuntimeException("Test exception"));

        // Act
        service.ingest(event);

        // Assert - should not throw exception, but log error
        verify(complaintIngestionService).ingest(any());
    }
}
