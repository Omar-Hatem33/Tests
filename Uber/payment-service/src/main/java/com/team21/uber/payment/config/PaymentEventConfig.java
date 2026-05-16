package com.team21.uber.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventConfig {

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange("payment.events");
    }

    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange("ride.events");
    }

    @Bean
    public DirectExchange paymentSagaDlx() {
        return new DirectExchange("payment.saga-listener.dlx");
    }

    @Bean
    public Queue paymentSagaDlq() {
        return QueueBuilder.durable("payment.saga-listener.dlq").build();
    }

    @Bean
    public Binding paymentSagaDlqBinding(Queue paymentSagaDlq,
                                         DirectExchange paymentSagaDlx) {
        return BindingBuilder.bind(paymentSagaDlq)
                .to(paymentSagaDlx)
                .with("payment.saga-listener.dlq");
    }

    @Bean
    public Queue paymentSagaQueue() {
        return QueueBuilder.durable("payment.saga-listener")
                .withArgument("x-dead-letter-exchange", "payment.saga-listener.dlx")
                .withArgument("x-dead-letter-routing-key", "payment.saga-listener.dlq")
                .build();
    }

    @Bean
    public Binding bindRideCompleted(Queue paymentSagaQueue,
                                     TopicExchange rideEventsExchange) {
        return BindingBuilder.bind(paymentSagaQueue)
                .to(rideEventsExchange)
                .with("ride.completed");
    }

    @Bean
    public Binding bindRideCancelled(Queue paymentSagaQueue,
                                     TopicExchange rideEventsExchange) {
        return BindingBuilder.bind(paymentSagaQueue)
                .to(rideEventsExchange)
                .with("ride.cancelled");
    }
}