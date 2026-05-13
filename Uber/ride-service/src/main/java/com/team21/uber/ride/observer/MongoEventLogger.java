package com.team21.uber.ride.observer;

import com.team21.uber.ride.event.EventFactory;
import com.team21.uber.ride.event.EventType;
import com.team21.uber.ride.event.MongoEvent;
import com.team21.uber.ride.event.RideEvent;
import com.team21.uber.ride.repository.RideEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final RideEventRepository rideEventRepository;
    private final EventType boundEventType;

    public MongoEventLogger(RideEventRepository rideEventRepository, EventType boundEventType) {
        this.rideEventRepository = rideEventRepository;
        this.boundEventType = boundEventType;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = (Map<String, Object>) payload;

            Map<String, Object> params = new HashMap<>(payloadMap);
            params.put("action", eventType);
            params.put("timestamp", LocalDateTime.now());

            MongoEvent event = EventFactory.createEvent(boundEventType, params);
            rideEventRepository.save((RideEvent) event);
        } catch (Exception e) {
            log.warn("Failed to log event to MongoDB: {}", e.getMessage(), e);
        }
    }
}
