package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

/**
 * Consumes SES feedback events from SQS and processes them through the existing bounce/complaint pipelines.
 * Long-polls SQS with configurable wait time and batch size.
 * Messages are only deleted after successful processing.
 */
@Component
@ConditionalOnProperty(prefix = "email-platform.ses-feedback", name = "enabled", havingValue = "true")
public class SesFeedbackConsumer {

    private static final Logger log = LoggerFactory.getLogger(SesFeedbackConsumer.class);

    private final SqsClient sqsClient;
    private final EmailPlatformProperties properties;
    private final SesFeedbackParser parser;
    private final SesBounceIngestionService bounceIngestionService;
    private final SesComplaintIngestionService complaintIngestionService;

    public SesFeedbackConsumer(
            SqsClient sqsClient,
            EmailPlatformProperties properties,
            SesFeedbackParser parser,
            SesBounceIngestionService bounceIngestionService,
            SesComplaintIngestionService complaintIngestionService
    ) {
        this.sqsClient = sqsClient;
        this.properties = properties;
        this.parser = parser;
        this.bounceIngestionService = bounceIngestionService;
        this.complaintIngestionService = complaintIngestionService;
    }

    @Scheduled(fixedDelayString = "${email-platform.ses-feedback.poll-ms:1000}")
    public void pollAndProcess() {
        EmailPlatformProperties.SesFeedback config = properties.getSesFeedback();
        
        if (!config.isEnabled()) {
            return;
        }

        String queueUrl = config.getQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            log.debug("SES feedback: queue URL not configured, skipping poll");
            return;
        }

        try {
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(config.getMaxMessages())
                    .waitTimeSeconds(config.getWaitSeconds())
                    .build();

            List<Message> messages = sqsClient.receiveMessage(request).messages();
            
            if (messages.isEmpty()) {
                return;
            }

            log.info("SES feedback: received {} messages from SQS", messages.size());

            for (Message message : messages) {
                processMessage(message, queueUrl);
            }

        } catch (Exception e) {
            log.error("SES feedback: error receiving messages from SQS", e);
        }
    }

    private void processMessage(Message message, String queueUrl) {
        String messageId = message.messageId();
        String body = message.body();

        if (body == null || body.isBlank()) {
            log.warn("SES feedback: empty message body, deleting messageId={}", messageId);
            deleteMessage(message.receiptHandle(), queueUrl);
            return;
        }

        try {
            SesFeedbackEvent event = parser.parse(body);
            
            if (event == null) {
                // Parser returned null (e.g., SNS subscription confirmation)
                log.info("SES feedback: parser returned null, deleting messageId={}", messageId);
                deleteMessage(message.receiptHandle(), queueUrl);
                return;
            }

            // Route to appropriate ingestion service
            if (event.isBounce()) {
                bounceIngestionService.ingest(event);
            } else if (event.isComplaint()) {
                complaintIngestionService.ingest(event);
            } else {
                log.warn("SES feedback: unsupported event type {}, deleting messageId={}", event.eventType(), messageId);
                deleteMessage(message.receiptHandle(), queueUrl);
                return;
            }

            // Only delete after successful processing
            deleteMessage(message.receiptHandle(), queueUrl);
            log.debug("SES feedback: processed and deleted messageId={}", messageId);

        } catch (Exception e) {
            log.error("SES feedback: failed to process messageId={}, leaving in SQS for retry", messageId, e);
            // Do NOT delete the message - let SQS visibility timeout handle retries
        }
    }

    private void deleteMessage(String receiptHandle, String queueUrl) {
        try {
            DeleteMessageRequest request = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
            sqsClient.deleteMessage(request);
        } catch (Exception e) {
            log.error("SES feedback: failed to delete message", e);
        }
    }
}
