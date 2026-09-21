package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.complaint.ComplaintIngestionRequest;
import com.texto.emailplatform.complaint.ComplaintIngestionResult;
import com.texto.emailplatform.complaint.ComplaintIngestionService;
import com.texto.emailplatform.complaint.ComplaintType;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts SES complaint events to {@link ComplaintIngestionService}.
 * Exceptions propagate so {@link SesFeedbackConsumer} does not delete the SQS message.
 */
@Service
public class SesComplaintIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SesComplaintIngestionService.class);
    static final String PROVIDER = "AWS_SES";

    private final ComplaintIngestionService complaintIngestionService;
    private final EmailMessageRepository emailMessageRepository;

    public SesComplaintIngestionService(
            ComplaintIngestionService complaintIngestionService,
            EmailMessageRepository emailMessageRepository
    ) {
        this.complaintIngestionService = complaintIngestionService;
        this.emailMessageRepository = emailMessageRepository;
    }

    public void ingest(SesFeedbackEvent event) {
        if (event == null || !event.isComplaint()) {
            throw new UnprocessableSesFeedbackException("invalid_ses_complaint");
        }

        String providerMessageId = event.messageId();
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new UnprocessableSesFeedbackException("missing_provider_message_id");
        }
        if (event.complainedRecipients() == null || event.complainedRecipients().isEmpty()) {
            throw new UnprocessableSesFeedbackException("missing_complained_recipients");
        }

        var messages = emailMessageRepository.findByProviderMessageId(providerMessageId);
        if (messages.isEmpty()) {
            throw new UnresolvedSesFeedbackException("unmatched_provider_message_id");
        }
        if (messages.size() > 1) {
            throw new UnresolvedSesFeedbackException("ambiguous_provider_message_id");
        }

        for (SesFeedbackEvent.ComplainedRecipient complainedRecipient : event.complainedRecipients()) {
            ingestRecipient(event, providerMessageId, complainedRecipient);
        }
    }

    private void ingestRecipient(
            SesFeedbackEvent event,
            String providerMessageId,
            SesFeedbackEvent.ComplainedRecipient complainedRecipient
    ) {
        if (complainedRecipient == null
                || complainedRecipient.emailAddress() == null
                || complainedRecipient.emailAddress().isBlank()) {
            log.warn("SES complaint: skipping blank recipient providerMessageId={}", providerMessageId);
            return;
        }

        String complaintType = mapComplaintType(event.complaintFeedbackType());
        Instant occurredAt = event.timestamp() != null ? event.timestamp() : Instant.now();

        Map<String, String> metadata = new LinkedHashMap<>();
        if (event.metadata() != null) {
            metadata.putAll(event.metadata());
        }
        metadata.put("source", event.source() != null ? event.source() : PROVIDER);

        ComplaintIngestionRequest request = new ComplaintIngestionRequest(
                PROVIDER,
                event.feedbackId(),
                null,
                providerMessageId,
                complainedRecipient.emailAddress(),
                null,
                complaintType,
                occurredAt,
                metadata
        );

        ComplaintIngestionResult result = complaintIngestionService.ingestFromTrustedSes(request);
        log.info(
                "SES complaint ingested providerMessageId={} complaintType={} result={}",
                providerMessageId,
                complaintType,
                result.getClass().getSimpleName()
        );
    }

    private String mapComplaintType(String sesComplaintFeedbackType) {
        if (sesComplaintFeedbackType == null || sesComplaintFeedbackType.isBlank()) {
            return ComplaintType.UNKNOWN.name();
        }
        return switch (sesComplaintFeedbackType.toLowerCase()) {
            case "abuse" -> ComplaintType.ABUSE.name();
            case "spam" -> ComplaintType.SPAM.name();
            case "fraud" -> ComplaintType.FRAUD.name();
            case "virus" -> ComplaintType.ABUSE.name();
            case "other" -> ComplaintType.UNKNOWN.name();
            default -> ComplaintType.UNKNOWN.name();
        };
    }
}
