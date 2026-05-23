package com.team21.uber.location.events;

import com.team21.uber.contracts.events.RideCancelledEvent;
import com.team21.uber.contracts.events.RideCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// Audit-only consumer of ride lifecycle events. Closes the location stream
// for a ride when it completes or is cancelled.
@Component
@RabbitListener(queues = RabbitConfig.LOCATION_RIDE_QUEUE)
public class RideSagaListener {

    private static final Logger log = LoggerFactory.getLogger(RideSagaListener.class);

    @RabbitHandler
    public void onRideCompleted(RideCompletedEvent rc) {
        log.info("location-service consumed ride.completed rideId={} driverId={}",
                rc.rideId(), rc.driverId());
    }

    @RabbitHandler
    public void onRideCancelled(RideCancelledEvent rx) {
        log.info("location-service consumed ride.cancelled rideId={} driverId={} reason={}",
                rx.rideId(), rx.driverId(), rx.reason());
    }

    @RabbitHandler(isDefault = true)
    public void onUnknown(Object payload) {
        log.warn("location-service got unexpected payload: {}", payload == null ? "null" : payload.getClass());
    }
}
