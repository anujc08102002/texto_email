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
 * Consumes SES feedback events from SQS and processes them through bounce/complaint pipelines.
 *
 * <p>Lifecycle: receive → process → delete only after successful handling.
 * Processing exceptions leave the message for SQS retry and queue redrive/DLQ.
 *
 * <p>SNS SubscriptionConfirmation / UnsubscribeConfirmation are control messages: they are
 * acknowledged (deleted) so they do not retry forever. Malformed or unsupported feedback is
 * not deleted.
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
                    .visibilityTimeout(config.getVisibilityTimeoutSeconds())
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

    void processMessage(Message message, String queueUrl) {
        String messageId = message.messageId();
        String body = message.body();

        try {
            SesFeedbackParseResult parsed = parser.parse(body);

            switch (parsed) {
                case SesFeedbackParseResult.Control control -> {
                    log.info(
                            "SES feedback: SNS control message type={} messageId={} (delete, not retry)",
                            control.type(),
                            messageId
                    );
                    deleteMessage(message.receiptHandle(), queueUrl);
                }
                case SesFeedbackParseResult.Unprocessable unprocessable -> {
                    throw new UnprocessableSesFeedbackException(unprocessable.reason());
                }
                case SesFeedbackParseResult.Feedback feedback -> {
                    SesFeedbackEvent event = feedback.event();
                    if (event.isBounce()) {
                        bounceIngestionService.ingest(event);
                    } else if (event.isComplaint()) {
                        complaintIngestionService.ingest(event);
                    } else {
                        throw new UnprocessableSesFeedbackException("unsupported_event_type:" + event.eventType());
                    }
                    deleteMessage(message.receiptHandle(), queueUrl);
                    log.debug("SES feedback: processed and deleted messageId={}", messageId);
                }
            }
        } catch (UnresolvedSesFeedbackException e) {
            log.warn("SES feedback: unresolved correlation messageId={}, leaving in SQS for retry: {}", messageId, e.getMessage());
        } catch (UnprocessableSesFeedbackException e) {
            log.error("SES feedback: unprocessable messageId={}, leaving in SQS for retry/DLQ: {}", messageId, e.getMessage());
        } catch (Exception e) {
            log.error("SES feedback: failed to process messageId={}, leaving in SQS for retry", messageId, e);
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
