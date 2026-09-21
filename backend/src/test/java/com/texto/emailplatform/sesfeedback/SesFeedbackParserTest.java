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

        SesFeedbackEvent event = feedback(parser.parse(rawBounce));
        assertTrue(event.isBounce());
        assertEquals("EXAMPLE7d19a832-0800-4056-9124-a8bb9cb01516-000000", event.messageId());
        assertEquals(1, event.bouncedRecipients().size());
        assertEquals("recipient@example.com", event.bouncedRecipients().getFirst().emailAddress());
    }

    @Test
    void parseSnsEnvelopeWithSesBounce() {
        String snsEnvelope = """
                {
                    "Type": "Notification",
                    "MessageId": "notification-id",
                    "TopicArn": "arn:aws:sns:ap-south-1:000000000000:texto-email-feedback",
                    "Message": "{\\"eventType\\":\\"Bounce\\",\\"mail\\":{\\"messageId\\":\\"EXAMPLE\\"},\\"bounce\\":{\\"bounceType\\":\\"Permanent\\"}}",
                    "Timestamp": "2024-01-15T10:30:00Z"
                }
                """;

        SesFeedbackEvent event = feedback(parser.parse(snsEnvelope));
        assertTrue(event.isBounce());
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

        SesFeedbackEvent event = feedback(parser.parse(rawComplaint));
        assertTrue(event.isComplaint());
        assertEquals("abuse", event.complaintFeedbackType());
        assertEquals(1, event.complainedRecipients().size());
    }

    @Test
    void parseSnsEnvelopeWithSesComplaint() {
        String snsEnvelope = """
                {
                    "Type": "Notification",
                    "MessageId": "notification-id",
                    "TopicArn": "arn:aws:sns:ap-south-1:000000000000:texto-email-feedback",
                    "Message": "{\\"eventType\\":\\"Complaint\\",\\"mail\\":{\\"messageId\\":\\"EXAMPLE\\"},\\"complaint\\":{\\"complaintFeedbackType\\":\\"spam\\"}}",
                    "Timestamp": "2024-01-15T10:30:00Z"
                }
                """;

        assertTrue(feedback(parser.parse(snsEnvelope)).isComplaint());
    }

    @Test
    void subscriptionConfirmationIsControlNotUnprocessable() {
        String subscriptionConfirmation = """
                {
                    "Type": "SubscriptionConfirmation",
                    "MessageId": "notification-id",
                    "TopicArn": "arn:aws:sns:ap-south-1:000000000000:texto-email-feedback",
                    "Message": "You have chosen to subscribe...",
                    "Timestamp": "2024-01-15T10:30:00Z"
                }
                """;

        SesFeedbackParseResult result = parser.parse(subscriptionConfirmation);
        assertInstanceOf(SesFeedbackParseResult.Control.class, result);
        assertEquals("SubscriptionConfirmation", ((SesFeedbackParseResult.Control) result).type());
    }

    @Test
    void unsupportedEventTypeIsUnprocessable() {
        String unsupportedEvent = """
                {
                    "eventType": "Delivery",
                    "mail": {
                        "messageId": "EXAMPLE"
                    }
                }
                """;

        SesFeedbackParseResult result = parser.parse(unsupportedEvent);
        assertInstanceOf(SesFeedbackParseResult.Unprocessable.class, result);
        assertTrue(((SesFeedbackParseResult.Unprocessable) result).reason().contains("unsupported_event_type"));
    }

    @Test
    void handleMalformedJson() {
        assertInstanceOf(SesFeedbackParseResult.Unprocessable.class, parser.parse("{invalid json"));
    }

    @Test
    void handleNullBody() {
        assertInstanceOf(SesFeedbackParseResult.Unprocessable.class, parser.parse(null));
    }

    @Test
    void handleEmptyBody() {
        assertInstanceOf(SesFeedbackParseResult.Unprocessable.class, parser.parse(""));
    }

    private static SesFeedbackEvent feedback(SesFeedbackParseResult result) {
        assertInstanceOf(SesFeedbackParseResult.Feedback.class, result);
        return ((SesFeedbackParseResult.Feedback) result).event();
    }
}
