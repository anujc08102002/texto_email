package com.texto.emailplatform.webhook;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebhookQueueConfiguration {

    @Bean
    DirectExchange webhookExchange() {
        return new DirectExchange(WebhookQueues.WEBHOOK_EXCHANGE, true, false);
    }

    @Bean
    Queue webhookDeliveryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", WebhookQueues.WEBHOOK_EXCHANGE);
        args.put("x-dead-letter-routing-key", WebhookQueues.DLQ_ROUTING_KEY);
        return QueueBuilder.durable(WebhookQueues.DELIVERY_QUEUE).withArguments(args).build();
    }

    @Bean
    Queue webhookDlq() {
        return QueueBuilder.durable(WebhookQueues.DLQ).build();
    }

    @Bean
    Binding webhookDeliveryBinding(Queue webhookDeliveryQueue, DirectExchange webhookExchange) {
        return BindingBuilder.bind(webhookDeliveryQueue).to(webhookExchange).with(WebhookQueues.DELIVERY_ROUTING_KEY);
    }

    @Bean
    Binding webhookDlqBinding(Queue webhookDlq, DirectExchange webhookExchange) {
        return BindingBuilder.bind(webhookDlq).to(webhookExchange).with(WebhookQueues.DLQ_ROUTING_KEY);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            org.springframework.core.env.Environment environment
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(environment.getProperty(
                "spring.rabbitmq.listener.simple.auto-startup",
                Boolean.class,
                true
        ));
        return factory;
    }
}
