package com.texto.emailplatform.queue;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailQueueConfiguration {

    @Bean
    DirectExchange emailExchange() {
        return new DirectExchange(EmailQueues.EMAIL_EXCHANGE, true, false);
    }

    @Bean
    Queue outboundQueue() {
        return new Queue(EmailQueues.OUTBOUND_QUEUE, true);
    }

    @Bean
    Queue bounceQueue() {
        return new Queue(EmailQueues.BOUNCE_QUEUE, true);
    }

    @Bean
    Binding outboundBinding(Queue outboundQueue, DirectExchange emailExchange) {
        return BindingBuilder.bind(outboundQueue).to(emailExchange).with(EmailQueues.OUTBOUND_ROUTING_KEY);
    }

    @Bean
    Binding bounceBinding(Queue bounceQueue, DirectExchange emailExchange) {
        return BindingBuilder.bind(bounceQueue).to(emailExchange).with(EmailQueues.BOUNCE_ROUTING_KEY);
    }
}
