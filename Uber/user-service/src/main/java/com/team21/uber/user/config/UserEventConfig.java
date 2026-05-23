package com.team21.uber.user.config;


import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventConfig {

    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    //Producer
    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange("user.events");
    }

    //Consumer
    //ride.events
    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange("ride.events");
    }

    @Bean
    public Queue userRideSagaListenerQueue() {
        return QueueBuilder.durable("user.ride.saga-listener")
                .withArgument("x-dead-letter-exchange", "user.ride.saga-listener.dlx")
                .withArgument("x-dead-letter-routing-key", "user.ride.saga-listener.dlq")
                .build();
    }

    @Bean
    public Queue userRideSagaDlq() {
        return QueueBuilder.durable("user.ride.saga-listener.dlq").build();
    }

    @Bean
    public Binding rideCompletedBinding() {
        return BindingBuilder
                .bind(userRideSagaListenerQueue())
                .to(rideEventsExchange())
                .with("ride.completed");
    }

    @Bean
    public Binding rideCancelledBinding() {
        return BindingBuilder
                .bind(userRideSagaListenerQueue())
                .to(rideEventsExchange())
                .with("ride.cancelled");
    }
}
