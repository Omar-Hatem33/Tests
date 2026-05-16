package com.team21.uber.ride.messaging;

import com.team21.uber.ride.config.RideEventConfig;
import com.team21.uber.contracts.events.RidePlacedEvent;
import com.team21.uber.contracts.events.RideCompletedEvent;
import com.team21.uber.contracts.events.RideCancelledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RideEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RideEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RideEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishRidePlaced(RidePlacedEvent event) {
        String routingKey = "ride.placed";
        try {
            MDC.put("routingKey", routingKey);
            log.info("Publishing {} with rideId={}, userId={}, driverId={}",
                    routingKey, event.rideId(), event.userId(), event.driverId());
            rabbitTemplate.convertAndSend(
                    RideEventConfig.RIDE_EVENTS_EXCHANGE,
                    routingKey,
                    event
            );
            log.info("Successfully published {}", routingKey);
        } finally {
            MDC.remove("routingKey");
        }
    }

    public void publishRideCompleted(RideCompletedEvent event) {
        String routingKey = "ride.completed";
        try {
            MDC.put("routingKey", routingKey);
            log.info("Publishing {} with rideId={}, userId={}, driverId={}, fare={}",
                    routingKey, event.rideId(), event.userId(), event.driverId(), event.fare());
            rabbitTemplate.convertAndSend(
                    RideEventConfig.RIDE_EVENTS_EXCHANGE,
                    routingKey,
                    event
            );
            log.info("Successfully published {}", routingKey);
        } finally {
            MDC.remove("routingKey");
        }
    }

    public void publishRideCancelled(RideCancelledEvent event) {
        String routingKey = "ride.cancelled";
        try {
            MDC.put("routingKey", routingKey);
            log.info("Publishing {} with rideId={}, userId={}, driverId={}, reason={}",
                    routingKey, event.rideId(), event.userId(), event.driverId(), event.reason());
            rabbitTemplate.convertAndSend(
                    RideEventConfig.RIDE_EVENTS_EXCHANGE,
                    routingKey,
                    event
            );
            log.info("Successfully published {}", routingKey);
        } finally {
            MDC.remove("routingKey");
        }
    }
}

