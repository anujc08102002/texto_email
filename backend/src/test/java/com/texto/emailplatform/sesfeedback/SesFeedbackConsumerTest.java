package com.texto.emailplatform.sesfeedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesFeedbackConsumerTest {

    private static final String QUEUE = "https://sqs.example.invalid/queue";
    private static final String BOUNCE_BODY = """
            {"eventType":"Bounce","mail":{"messageId":"mid-1"},"bounce":{"bounceType":"Permanent","bouncedRecipients":[{"emailAddress":"a@example.com"}]}}
            """;
    private static final String COMPLAINT_BODY = """
            {"eventType":"Complaint","mail":{"messageId":"mid-1"},"complaint":{"feedbackId":"fb","complainedRecipients":[{"emailAddress":"a@example.com"}]}}
            """;

    @Mock
    private SqsClient sqsClient;
    @Mock
    private SesBounceIngestionService bounceIngestionService;
    @Mock
    private SesComplaintIngestionService complaintIngestionService;

    private EmailPlatformProperties properties;
    private SesFeedbackConsumer consumer;

    @BeforeEach
    void setUp() {
        properties = new EmailPlatformProperties();
        properties.getSesFeedback().setEnabled(true);
        properties.getSesFeedback().setQueueUrl(QUEUE);
        properties.getSesFeedback().setMaxMessages(5);
        properties.getSesFeedback().setWaitSeconds(12);
        properties.getSesFeedback().setVisibilityTimeoutSeconds(120);
        consumer = new SesFeedbackConsumer(
                sqsClient,
                properties,
                new SesFeedbackParser(new ObjectMapper()),
                bounceIngestionService,
                complaintIngestionService
        );
    }

    @Test
    void successfulBounceDeletesMessage() {
        stubReceive(message("b1", BOUNCE_BODY));

        consumer.pollAndProcess();

        verify(bounceIngestionService).ingest(any());
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void successfulComplaintDeletesMessage() {
        stubReceive(message("c1", COMPLAINT_BODY));

        consumer.pollAndProcess();

        verify(complaintIngestionService).ingest(any());
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void unresolvedBounceDoesNotDelete() {
        stubReceive(message("b1", BOUNCE_BODY));
        doThrow(new UnresolvedSesFeedbackException("unmatched_provider_message_id"))
                .when(bounceIngestionService).ingest(any());

        consumer.pollAndProcess();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void unresolvedComplaintDoesNotDelete() {
        stubReceive(message("c1", COMPLAINT_BODY));
        doThrow(new UnresolvedSesFeedbackException("ambiguous_provider_message_id"))
                .when(complaintIngestionService).ingest(any());

        consumer.pollAndProcess();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void unresolvedAmbiguousBounceDoesNotDelete() {
        stubReceive(message("b1", BOUNCE_BODY));
        doThrow(new UnresolvedSesFeedbackException("ambiguous_provider_message_id"))
                .when(bounceIngestionService).ingest(any());

        consumer.pollAndProcess();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void bounceFailureDoesNotDelete() {
        stubReceive(message("b1", BOUNCE_BODY));
        doThrow(new RuntimeException("db down")).when(bounceIngestionService).ingest(any());

        consumer.pollAndProcess();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void unresolvedUnmatchedComplaintDoesNotDelete() {
        stubReceive(message("c1", COMPLAINT_BODY));
        doThrow(new UnresolvedSesFeedbackException("unmatched_provider_message_id"))
                .when(complaintIngestionService).ingest(any());

        consumer.pollAndProcess();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void complaintFailureDoesNotDelete() {
        stubReceive(message("c1", COMPLAINT_BODY));
        doThrow(new RuntimeException("db down")).when(complaintIngestionService).ingest(any());

        consumer.pollAndProcess();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void visibilityTimeoutIsPassedToReceive() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().build());

        consumer.pollAndProcess();

        ArgumentCaptor<ReceiveMessageRequest> captor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsClient).receiveMessage(captor.capture());
        assertThat(captor.getValue().visibilityTimeout()).isEqualTo(120);
        assertThat(captor.getValue().waitTimeSeconds()).isEqualTo(12);
        assertThat(captor.getValue().maxNumberOfMessages()).isEqualTo(5);
    }

    @Test
    void malformedFeedbackIsNotDeleted() {
        stubReceive(message("m1", "{not-json"));

        consumer.pollAndProcess();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(bounceIngestionService, never()).ingest(any());
    }

    @Test
    void unsupportedFeedbackIsNotDeleted() {
        stubReceive(message("d1", """
                {"eventType":"Delivery","mail":{"messageId":"mid"}}
                """));

        consumer.pollAndProcess();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void subscriptionConfirmationIsDeletedWithoutIngest() {
        stubReceive(message("s1", """
                {"Type":"SubscriptionConfirmation","Message":"confirm","MessageId":"x"}
                """));

        consumer.pollAndProcess();

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
        verify(bounceIngestionService, never()).ingest(any());
        verify(complaintIngestionService, never()).ingest(any());
    }

    private void stubReceive(Message message) {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
    }

    private static Message message(String id, String body) {
        return Message.builder().messageId(id).receiptHandle("rh-" + id).body(body).build();
    }
}
