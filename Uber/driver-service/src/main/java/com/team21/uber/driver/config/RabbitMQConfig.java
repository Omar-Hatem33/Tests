package com.team21.uber.driver.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ── Exchanges ─────────────────────────────────────────────────

    /** TopicExchange for events published BY driver-service */
    public static final String DRIVER_EVENTS_EXCHANGE = "driver.events";

    /** TopicExchange that ride-service publishes to — driver-service consumes from it */
    public static final String RIDE_EVENTS_EXCHANGE = "ride.events";

    // ── Routing keys consumed by driver-service ───────────────────
    public static final String RIDE_PLACED_KEY    = "ride.placed";
    public static final String RIDE_COMPLETED_KEY = "ride.completed";
    public static final String RIDE_CANCELLED_KEY = "ride.cancelled";

    // ── Queue names ───────────────────────────────────────────────
    public static final String DRIVER_RIDE_SAGA_QUEUE = "driver.ride.saga-listener";
    public static final String DRIVER_RIDE_SAGA_DLQ   = "driver.ride.saga-listener.dlq";

    // ── Routing keys published BY driver-service ──────────────────
    public static final String DRIVER_STATUS_CHANGED_KEY   = "driver.status-changed";
    public static final String DRIVER_RATED_KEY            = "driver.rated";
    public static final String DRIVER_DOCUMENT_VERIFIED_KEY = "driver.document.verified";

    // ── Exchange beans ────────────────────────────────────────────

    @Bean
    public TopicExchange driverEventsExchange() {
        return new TopicExchange(DRIVER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange rideEventsExchange() {
        return new TopicExchange(RIDE_EVENTS_EXCHANGE, true, false);
    }

    // ── DLQ ──────────────────────────────────────────────────────

    @Bean
    public Queue driverRideSagaDlq() {
        return QueueBuilder.durable(DRIVER_RIDE_SAGA_DLQ).build();
    }

    // ── Main queue with x-dead-letter-exchange pointing to DLQ ───

    @Bean
    public Queue driverRideSagaQueue() {
        return QueueBuilder.durable(DRIVER_RIDE_SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", "")         // default exchange
                .withArgument("x-dead-letter-routing-key", DRIVER_RIDE_SAGA_DLQ)
                .build();
    }

    // ── Bindings: driver listens for ride.placed / completed / cancelled ──

    @Bean
    public Binding ridePlacedBinding(Queue driverRideSagaQueue, TopicExchange rideEventsExchange) {
        return BindingBuilder.bind(driverRideSagaQueue)
                .to(rideEventsExchange)
                .with(RIDE_PLACED_KEY);
    }

    @Bean
    public Binding rideCompletedBinding(Queue driverRideSagaQueue, TopicExchange rideEventsExchange) {
        return BindingBuilder.bind(driverRideSagaQueue)
                .to(rideEventsExchange)
                .with(RIDE_COMPLETED_KEY);
    }

    @Bean
    public Binding rideCancelledBinding(Queue driverRideSagaQueue, TopicExchange rideEventsExchange) {
        return BindingBuilder.bind(driverRideSagaQueue)
                .to(rideEventsExchange)
                .with(RIDE_CANCELLED_KEY);
    }

    // ── JSON message converter ────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
