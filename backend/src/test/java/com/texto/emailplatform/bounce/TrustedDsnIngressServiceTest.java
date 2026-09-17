package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.queue.EmailQueues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class TrustedDsnIngressServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesRawDsnToBounceQueue() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        TrustedDsnIngressService ingress = new TrustedDsnIngressService(
                rabbitTemplate,
                properties,
                new BounceTokenExtractor(properties)
        );
        byte[] raw = "dsn".getBytes();
        ingress.accept("bounce+0123456789abcdef0123456789abcdef@bounce.texto.test", raw);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(EmailQueues.EMAIL_EXCHANGE), eq(EmailQueues.BOUNCE_ROUTING_KEY), captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo(raw);
        assertThat(captor.getValue().getMessageProperties().getHeaders().get(EmailQueues.ENVELOPE_RECIPIENT_HEADER))
                .isEqualTo("bounce+0123456789abcdef0123456789abcdef@bounce.texto.test");
    }

    @Test
    void rejectsOversizedPayload() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getBounce().setMaxRfc822Bytes(4);
        TrustedDsnIngressService ingress = new TrustedDsnIngressService(
                rabbitTemplate,
                properties,
                new BounceTokenExtractor(properties)
        );
        assertThatThrownBy(() -> ingress.accept("bounce+0123456789abcdef0123456789abcdef@bounce.texto.test", "too-big".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("oversized");
    }
}
