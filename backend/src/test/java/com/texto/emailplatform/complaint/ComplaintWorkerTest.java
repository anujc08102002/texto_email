package com.texto.emailplatform.complaint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.Channel;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class ComplaintWorkerTest {

    @Mock
    private ComplaintIngestionService complaintIngestionService;

    @Mock
    private Channel channel;

    private ComplaintWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ComplaintWorker(
                complaintIngestionService,
                JsonMapper.builder().findAndAddModules().build(),
                new EmailPlatformProperties()
        );
    }

    @Test
    void successfulIngestAcknowledges() throws Exception {
        when(complaintIngestionService.ingest(any())).thenReturn(ComplaintIngestionResult.accepted(null));
        worker.onMessage(json("{}"), channel, 7L);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void databaseFailureRequeues() throws Exception {
        when(complaintIngestionService.ingest(any())).thenThrow(new QueryTimeoutException("db"));
        worker.onMessage(json("{}"), channel, 8L);
        verify(channel).basicNack(8L, false, true);
    }

    @Test
    void unexpectedFailureGoesToDlq() throws Exception {
        when(complaintIngestionService.ingest(any())).thenThrow(new IllegalStateException("permanent"));
        worker.onMessage(json("{}"), channel, 9L);
        verify(channel).basicNack(9L, false, false);
    }

    @Test
    void malformedJsonIsPersistedNotThrown() throws Exception {
        when(complaintIngestionService.ingestMalformed(any(), any())).thenReturn(
                ComplaintIngestionResult.parseFailed("malformed_json", null)
        );
        worker.onMessage(json("{"), channel, 10L);
        verify(complaintIngestionService).ingestMalformed(any(), org.mockito.ArgumentMatchers.eq("malformed_json"));
        verify(channel).basicAck(10L, false);
    }

    @Test
    void oversizedIsPersistedNotThrown() throws Exception {
        when(complaintIngestionService.ingestMalformed(any(), any())).thenReturn(
                ComplaintIngestionResult.parseFailed("oversized", null)
        );
        byte[] huge = new byte[70_000];
        worker.onMessage(new Message(huge, new MessageProperties()), channel, 11L);
        verify(complaintIngestionService).ingestMalformed(any(), org.mockito.ArgumentMatchers.eq("oversized"));
        verify(channel).basicAck(11L, false);
    }

    private static Message json(String body) {
        return new Message(body.getBytes(), new MessageProperties());
    }
}
