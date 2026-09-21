package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.complaint.ComplaintIngestionRequest;
import com.texto.emailplatform.complaint.ComplaintIngestionService;
import com.texto.emailplatform.complaint.ComplaintIngestionResult;
import com.texto.emailplatform.complaint.ComplaintType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts SES complaint events to the existing complaint processing pipeline.
 * Reuses ComplaintIngestionService for correlation and policy application.
 */
@Service
public class SesComplaintIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SesComplaintIngestionService.class);
    private static final String PROVIDER = "AWS_SES";

    private final ComplaintIngestionService complaintIngestionService;

    public SesComplaintIngestionService(ComplaintIngestionService complaintIngestionService) {
        this.complaintIngestionService = complaintIngestionService;
    }

    public void ingest(SesFeedbackEvent event) {
        if (event == null || !event.isComplaint()) {
            log.warn("Invalid SES complaint event");
            return;
        }

        String providerMessageId = event.messageId();
        if (providerMessageId == null || providerMessageId.isBlank()) {
            log.warn("SES complaint event missing provider message ID");
            return;
        }

        // Get the first complained recipient (SES typically sends one per event)
        if (event.complainedRecipients() == null || event.complainedRecipients().isEmpty()) {
            log.warn("SES complaint event has no complained recipients");
            return;
        }

        String recipient = event.complainedRecipients().getFirst().emailAddress();
        if (recipient == null || recipient.isBlank()) {
            log.warn("SES complaint event has invalid recipient");
            return;
        }

        // Map SES complaint fields to ComplaintIngestionRequest
        String complaintType = mapComplaintType(event.complaintFeedbackType());
        Instant occurredAt = event.timestamp() != null ? event.timestamp() : Instant.now();
        
        // Build metadata (sanitized)
        Map<String, String> metadata = new LinkedHashMap<>();
        if (event.metadata() != null) {
            metadata.putAll(event.metadata());
        }
        metadata.put("source", event.source() != null ? event.source() : "AWS_SES");

        ComplaintIngestionRequest request = new ComplaintIngestionRequest(
                PROVIDER,
                event.feedbackId(), // providerEventId
                null, // messageId (not available from SES)
                providerMessageId,
                recipient,
                null, // correlationToken (not available from SES)
                complaintType,
                occurredAt,
                metadata
        );

        try {
            ComplaintIngestionResult result = complaintIngestionService.ingest(request);
            log.info(
                    "SES complaint ingested providerMessageId={} recipient={} complaintType={} result={}",
                    providerMessageId,
                    recipient,
                    complaintType,
                    result.getClass().getSimpleName()
            );
        } catch (Exception e) {
            log.error("Failed to ingest SES complaint", e);
        }
    }

    private String mapComplaintType(String sesComplaintFeedbackType) {
        if (sesComplaintFeedbackType == null || sesComplaintFeedbackType.isBlank()) {
            return ComplaintType.UNKNOWN.name();
        }
        return switch (sesComplaintFeedbackType.toLowerCase()) {
            case "abuse" -> ComplaintType.ABUSE.name();
            case "spam" -> ComplaintType.SPAM.name();
            case "fraud" -> ComplaintType.FRAUD.name();
            case "virus" -> ComplaintType.ABUSE.name(); // Map virus to abuse
            case "other" -> ComplaintType.UNKNOWN.name();
            default -> ComplaintType.UNKNOWN.name();
        };
    }
}
