package com.texto.emailplatform.complaint;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.queue.EmailQueues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class TrustedComplaintIngressServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TrustedComplaintIngressService ingress;

    @BeforeEach
    void setUp() {
        ingress = new TrustedComplaintIngressService(
                rabbitTemplate,
                JsonMapper.builder().findAndAddModules().build(),
                new EmailPlatformProperties()
        );
    }

    @Test
    void queuesProviderNeutralPayload() {
        ingress.accept(ComplaintIngestionRequest.ofToken("0123456789abcdef0123456789abcdef", "alice@example.com"));
        verify(rabbitTemplate).send(eq(EmailQueues.EMAIL_EXCHANGE), eq(EmailQueues.COMPLAINT_ROUTING_KEY), any(Message.class));
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> ingress.accept(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
