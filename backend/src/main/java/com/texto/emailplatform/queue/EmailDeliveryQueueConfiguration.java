package com.texto.emailplatform.queue;

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
public class EmailDeliveryQueueConfiguration {

    @Bean
    DirectExchange emailDeliveryExchange() {
        return new DirectExchange(EmailDeliveryQueues.DELIVERY_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange emailDeliveryDlx() {
        return new DirectExchange(EmailDeliveryQueues.DLX, true, false);
    }

    @Bean
    DirectExchange emailDeliveryRetryExchange() {
        return new DirectExchange(EmailDeliveryQueues.RETRY_EXCHANGE, true, false);
    }

    @Bean
    Queue emailDeliveryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EmailDeliveryQueues.DLX);
        args.put("x-dead-letter-routing-key", EmailDeliveryQueues.DLQ_ROUTING_KEY);
        return QueueBuilder.durable(EmailDeliveryQueues.DELIVERY_QUEUE).withArguments(args).build();
    }

    @Bean
    Queue emailDeliveryDlq() {
        return QueueBuilder.durable(EmailDeliveryQueues.DLQ).build();
    }

    @Bean
    Binding emailDeliveryBinding(Queue emailDeliveryQueue, DirectExchange emailDeliveryExchange) {
        return BindingBuilder.bind(emailDeliveryQueue)
                .to(emailDeliveryExchange)
                .with(EmailDeliveryQueues.DELIVERY_ROUTING_KEY);
    }

    @Bean
    Binding emailDeliveryDlqBinding(Queue emailDeliveryDlq, DirectExchange emailDeliveryDlx) {
        return BindingBuilder.bind(emailDeliveryDlq)
                .to(emailDeliveryDlx)
                .with(EmailDeliveryQueues.DLQ_ROUTING_KEY);
    }

    @Bean
    Queue emailRetry30sQueue() {
        return ttlRetryQueue(EmailDeliveryQueues.RETRY_30S_QUEUE, 30_000);
    }

    @Bean
    Queue emailRetry2mQueue() {
        return ttlRetryQueue(EmailDeliveryQueues.RETRY_2M_QUEUE, 120_000);
    }

    @Bean
    Queue emailRetry10mQueue() {
        return ttlRetryQueue(EmailDeliveryQueues.RETRY_10M_QUEUE, 600_000);
    }

    @Bean
    Queue emailRetry30mQueue() {
        return ttlRetryQueue(EmailDeliveryQueues.RETRY_30M_QUEUE, 1_800_000);
    }

    @Bean
    Queue emailRetry2hQueue() {
        return ttlRetryQueue(EmailDeliveryQueues.RETRY_2H_QUEUE, 7_200_000);
    }

    @Bean
    Binding emailRetry30sBinding(Queue emailRetry30sQueue, DirectExchange emailDeliveryRetryExchange) {
        return BindingBuilder.bind(emailRetry30sQueue)
                .to(emailDeliveryRetryExchange)
                .with(EmailDeliveryQueues.RETRY_30S_KEY);
    }

    @Bean
    Binding emailRetry2mBinding(Queue emailRetry2mQueue, DirectExchange emailDeliveryRetryExchange) {
        return BindingBuilder.bind(emailRetry2mQueue)
                .to(emailDeliveryRetryExchange)
                .with(EmailDeliveryQueues.RETRY_2M_KEY);
    }

    @Bean
    Binding emailRetry10mBinding(Queue emailRetry10mQueue, DirectExchange emailDeliveryRetryExchange) {
        return BindingBuilder.bind(emailRetry10mQueue)
                .to(emailDeliveryRetryExchange)
                .with(EmailDeliveryQueues.RETRY_10M_KEY);
    }

    @Bean
    Binding emailRetry30mBinding(Queue emailRetry30mQueue, DirectExchange emailDeliveryRetryExchange) {
        return BindingBuilder.bind(emailRetry30mQueue)
                .to(emailDeliveryRetryExchange)
                .with(EmailDeliveryQueues.RETRY_30M_KEY);
    }

    @Bean
    Binding emailRetry2hBinding(Queue emailRetry2hQueue, DirectExchange emailDeliveryRetryExchange) {
        return BindingBuilder.bind(emailRetry2hQueue)
                .to(emailDeliveryRetryExchange)
                .with(EmailDeliveryQueues.RETRY_2H_KEY);
    }

    @Bean
    SimpleRabbitListenerContainerFactory emailDeliveryListenerContainerFactory(
            ConnectionFactory connectionFactory,
            org.springframework.core.env.Environment environment
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        factory.setAutoStartup(environment.getProperty(
                "spring.rabbitmq.listener.simple.auto-startup",
                Boolean.class,
                true
        ));
        return factory;
    }

    private static Queue ttlRetryQueue(String name, int ttlMs) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", ttlMs);
        args.put("x-dead-letter-exchange", EmailDeliveryQueues.DELIVERY_EXCHANGE);
        args.put("x-dead-letter-routing-key", EmailDeliveryQueues.DELIVERY_ROUTING_KEY);
        return QueueBuilder.durable(name).withArguments(args).build();
    }
}
