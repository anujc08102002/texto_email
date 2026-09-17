package com.texto.emailplatform.queue;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
    DirectExchange bounceDlx() {
        return new DirectExchange(EmailQueues.BOUNCE_DLX, true, false);
    }

    @Bean
    Queue bounceQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EmailQueues.BOUNCE_DLX);
        args.put("x-dead-letter-routing-key", EmailQueues.BOUNCE_DLQ_ROUTING_KEY);
        return QueueBuilder.durable(EmailQueues.BOUNCE_QUEUE).withArguments(args).build();
    }

    @Bean
    Queue bounceDlq() {
        return QueueBuilder.durable(EmailQueues.BOUNCE_DLQ).build();
    }

    @Bean
    Binding outboundBinding(Queue outboundQueue, DirectExchange emailExchange) {
        return BindingBuilder.bind(outboundQueue).to(emailExchange).with(EmailQueues.OUTBOUND_ROUTING_KEY);
    }

    @Bean
    Binding bounceBinding(Queue bounceQueue, DirectExchange emailExchange) {
        return BindingBuilder.bind(bounceQueue).to(emailExchange).with(EmailQueues.BOUNCE_ROUTING_KEY);
    }

    @Bean
    Binding bounceDlqBinding(Queue bounceDlq, DirectExchange bounceDlx) {
        return BindingBuilder.bind(bounceDlq).to(bounceDlx).with(EmailQueues.BOUNCE_DLQ_ROUTING_KEY);
    }

    @Bean
    DirectExchange complaintDlx() {
        return new DirectExchange(EmailQueues.COMPLAINT_DLX, true, false);
    }

    @Bean
    Queue complaintQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EmailQueues.COMPLAINT_DLX);
        args.put("x-dead-letter-routing-key", EmailQueues.COMPLAINT_DLQ_ROUTING_KEY);
        return QueueBuilder.durable(EmailQueues.COMPLAINT_QUEUE).withArguments(args).build();
    }

    @Bean
    Queue complaintDlq() {
        return QueueBuilder.durable(EmailQueues.COMPLAINT_DLQ).build();
    }

    @Bean
    Binding complaintBinding(Queue complaintQueue, DirectExchange emailExchange) {
        return BindingBuilder.bind(complaintQueue).to(emailExchange).with(EmailQueues.COMPLAINT_ROUTING_KEY);
    }

    @Bean
    Binding complaintDlqBinding(Queue complaintDlq, DirectExchange complaintDlx) {
        return BindingBuilder.bind(complaintDlq).to(complaintDlx).with(EmailQueues.COMPLAINT_DLQ_ROUTING_KEY);
    }
}
