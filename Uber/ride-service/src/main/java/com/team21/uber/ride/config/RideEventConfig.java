package com.team21.uber.ride.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RideEventConfig {

    public static final String RIDE_EVENTS_EXCHANGE = "ride.events";

    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange(RIDE_EVENTS_EXCHANGE);
    }
}

