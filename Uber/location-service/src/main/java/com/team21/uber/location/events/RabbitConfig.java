package com.team21.uber.location.events;

import com.team21.uber.contracts.events.Topology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

// Location service RabbitMQ wiring.
// Publishes location.events (location.tracked) and consumes ride.events for audit.
@Configuration
public class RabbitConfig {

    public static final String LOCATION_RIDE_QUEUE     = "location.ride.saga-listener";
    public static final String LOCATION_RIDE_QUEUE_DLQ = LOCATION_RIDE_QUEUE + Topology.DLQ_SUFFIX;
    public static final String RIDE_EVENTS_DLX         = Topology.RIDE_EVENTS     + Topology.DLX_SUFFIX;
    public static final String LOCATION_EVENTS_DLX     = Topology.LOCATION_EVENTS + Topology.DLX_SUFFIX;

    @Bean
    public TopicExchange locationEventsExchange() {
        return ExchangeBuilder.topicExchange(Topology.LOCATION_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange locationEventsDlx() {
        return ExchangeBuilder.topicExchange(LOCATION_EVENTS_DLX).durable(true).build();
    }

    @Bean
    public TopicExchange rideEventsExchange() {
        return ExchangeBuilder.topicExchange(Topology.RIDE_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange rideEventsDlx() {
        return ExchangeBuilder.topicExchange(RIDE_EVENTS_DLX).durable(true).build();
    }

    @Bean
    public Queue locationRideQueue() {
        return QueueBuilder.durable(LOCATION_RIDE_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange",    RIDE_EVENTS_DLX,
                        "x-dead-letter-routing-key", LOCATION_RIDE_QUEUE_DLQ))
                .build();
    }

    @Bean
    public Queue locationRideDlq() {
        return QueueBuilder.durable(LOCATION_RIDE_QUEUE_DLQ).build();
    }

    @Bean
    public Binding bindRideCompletedToLocation() {
        return BindingBuilder.bind(locationRideQueue()).to(rideEventsExchange()).with(Topology.RK_RIDE_COMPLETED);
    }

    @Bean
    public Binding bindRideCancelledToLocation() {
        return BindingBuilder.bind(locationRideQueue()).to(rideEventsExchange()).with(Topology.RK_RIDE_CANCELLED);
    }

    @Bean
    public Binding bindLocationRideDlq() {
        return BindingBuilder.bind(locationRideDlq()).to(rideEventsDlx()).with(LOCATION_RIDE_QUEUE_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(mc);
        t.setExchange(Topology.LOCATION_EVENTS);
        return t;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf, MessageConverter mc) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(mc);
        f.setDefaultRequeueRejected(false);
        return f;
    }
}
