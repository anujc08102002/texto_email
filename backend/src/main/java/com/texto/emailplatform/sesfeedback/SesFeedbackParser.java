package com.texto.emailplatform.sesfeedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses SES feedback events from SQS messages.
 * Handles both SNS envelope and raw SES event formats.
 */
@Component
public class SesFeedbackParser {

    private static final Logger log = LoggerFactory.getLogger(SesFeedbackParser.class);
    private static final String SNS_TYPE_SUBSCRIPTION_CONFIRMATION = "SubscriptionConfirmation";

    private final ObjectMapper objectMapper;

    public SesFeedbackParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse SQS message body. Returns null if message should be ignored (e.g., SNS subscription confirmation).
     */
    public SesFeedbackEvent parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            
            // Check if this is an SNS envelope
            if (root.has("Type") && root.has("Message")) {
                String type = root.get("Type").asText();
                if (SNS_TYPE_SUBSCRIPTION_CONFIRMATION.equals(type)) {
                    log.info("Ignoring SNS subscription confirmation message");
                    return null;
                }
                // Extract the SES event from the SNS Message field
                String message = root.get("Message").asText();
                return parseSesEvent(message);
            }
            
            // Otherwise, treat as raw SES event
            return parseSesEvent(body);
            
        } catch (Exception e) {
            log.error("Failed to parse SES feedback message", e);
            return null;
        }
    }

    private SesFeedbackEvent parseSesEvent(String json) throws Exception {
        JsonNode event = objectMapper.readTree(json);
        
        String eventType = event.has("eventType") ? event.get("eventType").asText() : null;
        if (eventType == null) {
            log.warn("SES event missing eventType field");
            return null;
        }

        if (!"Bounce".equals(eventType) && !"Complaint".equals(eventType)) {
            log.debug("Ignoring SES event type: {}", eventType);
            return null;
        }

        JsonNode mail = event.get("mail");
        String messageId = mail != null && mail.has("messageId") ? mail.get("messageId").asText() : null;
        
        List<String> destinations = new ArrayList<>();
        if (mail != null && mail.has("destination")) {
            for (JsonNode dest : mail.get("destination")) {
                destinations.add(dest.asText());
            }
        }
        
        String source = mail != null && mail.has("source") ? mail.get("source").asText() : null;
        Instant timestamp = mail != null && mail.has("timestamp") 
                ? Instant.parse(mail.get("timestamp").asText()) 
                : Instant.now();

        if ("Bounce".equals(eventType)) {
            return parseBounce(event, messageId, destinations, source, timestamp);
        } else {
            return parseComplaint(event, messageId, destinations, source, timestamp);
        }
    }

    private SesFeedbackEvent parseBounce(JsonNode event, String messageId, List<String> destinations, 
                                         String source, Instant timestamp) {
        JsonNode bounce = event.get("bounce");
        if (bounce == null) {
            log.warn("Bounce event missing bounce field");
            return null;
        }

        String bounceType = bounce.has("bounceType") ? bounce.get("bounceType").asText() : null;
        String bounceSubType = bounce.has("bounceSubType") ? bounce.get("bounceSubType").asText() : null;
        String action = bounce.has("action") ? bounce.get("action").asText() : null;
        String status = bounce.has("status") ? bounce.get("status").asText() : null;
        String diagnosticCode = bounce.has("diagnosticCode") ? bounce.get("diagnosticCode").asText() : null;
        String reportingMta = bounce.has("reportingMTA") ? bounce.get("reportingMTA").asText() : null;
        String remoteMta = bounce.has("remoteMtaIp") ? bounce.get("remoteMtaIp").asText() : null;
        String feedbackId = bounce.has("feedbackId") ? bounce.get("feedbackId").asText() : null;

        List<SesFeedbackEvent.BouncedRecipient> bouncedRecipients = new ArrayList<>();
        if (bounce.has("bouncedRecipients")) {
            for (JsonNode recipient : bounce.get("bouncedRecipients")) {
                String emailAddress = recipient.has("emailAddress") ? recipient.get("emailAddress").asText() : null;
                String recipientAction = recipient.has("action") ? recipient.get("action").asText() : null;
                String recipientStatus = recipient.has("status") ? recipient.get("status").asText() : null;
                String recipientDiagnostic = recipient.has("diagnosticCode") ? recipient.get("diagnosticCode").asText() : null;
                if (emailAddress != null) {
                    bouncedRecipients.add(new SesFeedbackEvent.BouncedRecipient(
                            emailAddress, recipientAction, recipientStatus, recipientDiagnostic
                    ));
                }
            }
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("bounceType", bounceType);
        metadata.put("bounceSubType", bounceSubType);
        if (bounceSubType != null) {
            metadata.put("bounceSubType", bounceSubType);
        }

        return new SesFeedbackEvent(
                "Bounce",
                messageId,
                destinations,
                source,
                timestamp,
                bounceType,
                bounceSubType,
                action,
                status,
                diagnosticCode,
                reportingMta,
                remoteMta,
                feedbackId,
                bouncedRecipients,
                null,
                null,
                metadata
        );
    }

    private SesFeedbackEvent parseComplaint(JsonNode event, String messageId, List<String> destinations,
                                            String source, Instant timestamp) {
        JsonNode complaint = event.get("complaint");
        if (complaint == null) {
            log.warn("Complaint event missing complaint field");
            return null;
        }

        String complaintFeedbackType = complaint.has("complaintFeedbackType") 
                ? complaint.get("complaintFeedbackType").asText() 
                : null;
        String feedbackId = complaint.has("feedbackId") ? complaint.get("feedbackId").asText() : null;

        List<SesFeedbackEvent.ComplainedRecipient> complainedRecipients = new ArrayList<>();
        if (complaint.has("complainedRecipients")) {
            for (JsonNode recipient : complaint.get("complainedRecipients")) {
                String emailAddress = recipient.has("emailAddress") ? recipient.get("emailAddress").asText() : null;
                if (emailAddress != null) {
                    complainedRecipients.add(new SesFeedbackEvent.ComplainedRecipient(emailAddress));
                }
            }
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        if (complaintFeedbackType != null) {
            metadata.put("complaintFeedbackType", complaintFeedbackType);
        }

        return new SesFeedbackEvent(
                "Complaint",
                messageId,
                destinations,
                source,
                timestamp,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                feedbackId,
                null,
                complaintFeedbackType,
                complainedRecipients,
                metadata
        );
    }
}
