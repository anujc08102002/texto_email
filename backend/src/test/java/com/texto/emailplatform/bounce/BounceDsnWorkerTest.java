package com.texto.emailplatform.bounce;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.texto.emailplatform.queue.EmailQueues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class BounceDsnWorkerTest {

    @Mock
    private DsnIngestionService dsnIngestionService;

    @Mock
    private Channel channel;

    private BounceDsnWorker worker;

    @BeforeEach
    void setUp() {
        worker = new BounceDsnWorker(dsnIngestionService);
    }

    @Test
    void successfulIngestAcknowledges() throws Exception {
        when(dsnIngestionService.ingest(any())).thenReturn(DsnIngestionResult.accepted(java.util.List.of()));
        worker.onMessage(message(), channel, 7L);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void databaseFailureRequeues() throws Exception {
        when(dsnIngestionService.ingest(any())).thenThrow(new QueryTimeoutException("db"));
        worker.onMessage(message(), channel, 8L);
        verify(channel).basicNack(8L, false, true);
    }

    @Test
    void unexpectedFailureGoesToDlq() throws Exception {
        when(dsnIngestionService.ingest(any())).thenThrow(new IllegalStateException("permanent"));
        worker.onMessage(message(), channel, 9L);
        verify(channel).basicNack(eq(9L), eq(false), eq(false));
    }

    private static Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(EmailQueues.ENVELOPE_RECIPIENT_HEADER, "bounce+0123456789abcdef0123456789abcdef@bounce.texto.test");
        return new Message("dsn".getBytes(), properties);
    }
}
