package com.team21.uber.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventConfig {

    public static final String PAYMENT_EXCHANGE = "payment.events";
    public static final String RIDE_EXCHANGE = "ride.events";
    public static final String SAGA_QUEUE = "payment.saga-listener";
    public static final String SAGA_DLQ = "payment.saga-listener.dlq";
    public static final String DLX = "payment.saga-listener.dlx";

    // ── Message Converter ─────────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    // ── Exchanges ─────────────────────────────────────────────────────

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange(RIDE_EXCHANGE);
    }

    // ── Dead Letter Exchange + Queue ──────────────────────────────────

    @Bean
    public DirectExchange paymentSagaDlx() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue paymentSagaDlq() {
        return QueueBuilder.durable(SAGA_DLQ).build();
    }

    @Bean
    public Binding paymentSagaDlqBinding() {
        return BindingBuilder.bind(paymentSagaDlq())
                .to(paymentSagaDlx())
                .with(SAGA_DLQ);
    }

    // ── Main Saga Queue ───────────────────────────────────────────────

    @Bean
    public Queue paymentSagaQueue() {
        return QueueBuilder.durable(SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", SAGA_DLQ)
                .build();
    }

    @Bean
    public Binding bindRideCompleted() {
        return BindingBuilder.bind(paymentSagaQueue())
                .to(rideEventsExchange())
                .with("ride.completed");
    }

    @Bean
    public Binding bindRideCancelled() {
        return BindingBuilder.bind(paymentSagaQueue())
                .to(rideEventsExchange())
                .with("ride.cancelled");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}