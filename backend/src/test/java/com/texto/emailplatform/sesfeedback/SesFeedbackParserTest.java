package com.texto.emailplatform.sesfeedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SesFeedbackParserTest {

    private SesFeedbackParser parser;

    @BeforeEach
    void setUp() {
        parser = new SesFeedbackParser(new ObjectMapper());
    }

    @Test
    void parseRawSesBounceEvent() {
        String rawBounce = """
                {
                    "eventType": "Bounce",
                    "mail": {
                        "messageId": "EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000",
                        "destination": ["recipient@example.com"],
                        "source": "sender@example.com",
                        "timestamp": "2024-01-15T10:30:00Z"
                    },
                    "bounce": {
                        "bounceType": "Permanent",
                        "bounceSubType": "General",
                        "action": "failed",
                        "status": "5.1.1",
                        "diagnosticCode": "550 5.1.1 User unknown",
                        "reportingMTA": "dns; smtp.example.com",
                        "remoteMtaIp": "192.0.2.1",
                        "feedbackId": "0000013f1c8e1a0c-1a3c3e3e-1a3c-3e3e-1a3c-3e3e1a3c3e3e",
                        "bouncedRecipients": [
                            {
                                "emailAddress": "recipient@example.com",
                                "action": "failed",
                                "status": "5.1.1",
                                "diagnosticCode": "550 5.1.1 User unknown"
                            }
                        ]
                    }
                }
                """;

        SesFeedbackEvent event = parser.parse(rawBounce);
        
        assertNotNull(event);
        assertTrue(event.isBounce());
        assertFalse(event.isComplaint());
        assertEquals("Bounce", event.eventType());
        assertEquals("EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000", event.messageId());
        assertEquals("Permanent", event.bounceType());
        assertEquals("General", event.bounceSubType());
        assertEquals("failed", event.action());
        assertEquals("5.1.1", event.status());
        assertEquals("550 5.1.1 User unknown", event.diagnosticCode());
        assertEquals(1, event.bouncedRecipients().size());
        assertEquals("recipient@example.com", event.bouncedRecipients().getFirst().emailAddress());
    }

    @Test
    void parseSnsEnvelopeWithSesBounce() {
        String snsEnvelope = """
                {
                    "Type": "Notification",
                    "MessageId": "notification-id",
                    "TopicArn": "arn:aws:sns:ap-south-1:172159866090:texto-email-feedback",
                    "Message": "{\\"eventType\\":\\"Bounce\\",\\"mail\\":{\\"messageId\\":\\"EXAMPLE\\"},\\"bounce\\":{\\"bounceType\\":\\"Permanent\\"}}",
                    "Timestamp": "2024-01-15T10:30:00Z"
                }
                """;

        SesFeedbackEvent event = parser.parse(snsEnvelope);
        
        assertNotNull(event);
        assertTrue(event.isBounce());
        assertEquals("Bounce", event.eventType());
    }

    @Test
    void parseRawSesComplaintEvent() {
        String rawComplaint = """
                {
                    "eventType": "Complaint",
                    "mail": {
                        "messageId": "EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000",
                        "destination": ["recipient@example.com"],
                        "source": "sender@example.com",
                        "timestamp": "2024-01-15T10:30:00Z"
                    },
                    "complaint": {
                        "complaintFeedbackType": "abuse",
                        "feedbackId": "0000013f1c8e1a0c-1a3c3e3e-1a3c-3e3e-1a3c-3e3e1a3c3e3e",
                        "complainedRecipients": [
                            {
                                "emailAddress": "recipient@example.com"
                            }
                        ]
                    }
                }
                """;

        SesFeedbackEvent event = parser.parse(rawComplaint);
        
        assertNotNull(event);
        assertTrue(event.isComplaint());
        assertFalse(event.isBounce());
        assertEquals("Complaint", event.eventType());
        assertEquals("abuse", event.complaintFeedbackType());
        assertEquals(1, event.complainedRecipients().size());
        assertEquals("recipient@example.com", event.complainedRecipients().getFirst().emailAddress());
    }

    @Test
    void parseSnsEnvelopeWithSesComplaint() {
        String snsEnvelope = """
                {
                    "Type": "Notification",
                    "MessageId": "notification-id",
                    "TopicArn": "arn:aws:sns:ap-south-1:172159866090:texto-email-feedback",
                    "Message": "{\\"eventType\\":\\"Complaint\\",\\"mail\\":{\\"messageId\\":\\"EXAMPLE\\"},\\"complaint\\":{\\"complaintFeedbackType\\":\\"spam\\"}}",
                    "Timestamp": "2024-01-15T10:30:00Z"
                }
                """;

        SesFeedbackEvent event = parser.parse(snsEnvelope);
        
        assertNotNull(event);
        assertTrue(event.isComplaint());
        assertEquals("Complaint", event.eventType());
    }

    @Test
    void ignoreSnsSubscriptionConfirmation() {
        String subscriptionConfirmation = """
                {
                    "Type": "SubscriptionConfirmation",
                    "MessageId": "notification-id",
                    "TopicArn": "arn:aws:sns:ap-south-1:172159866090:texto-email-feedback",
                    "Message": "You have chosen to subscribe...",
                    "Timestamp": "2024-01-15T10:30:00Z"
                }
                """;

        SesFeedbackEvent event = parser.parse(subscriptionConfirmation);
        
        assertNull(event);
    }

    @Test
    void ignoreUnsupportedEventType() {
        String unsupportedEvent = """
                {
                    "eventType": "Delivery",
                    "mail": {
                        "messageId": "EXAMPLE"
                    }
                }
                """;

        SesFeedbackEvent event = parser.parse(unsupportedEvent);
        
        assertNull(event);
    }

    @Test
    void handleMalformedJson() {
        String malformed = "{invalid json";
        
        SesFeedbackEvent event = parser.parse(malformed);
        
        assertNull(event);
    }

    @Test
    void handleNullBody() {
        SesFeedbackEvent event = parser.parse(null);
        
        assertNull(event);
    }

    @Test
    void handleEmptyBody() {
        SesFeedbackEvent event = parser.parse("");
        
        assertNull(event);
    }
}
