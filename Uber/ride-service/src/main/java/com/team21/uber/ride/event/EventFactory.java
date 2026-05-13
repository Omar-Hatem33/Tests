package com.team21.uber.ride.event;

import java.time.LocalDateTime;
import java.util.Map;

public class EventFactory {

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        String action = (String) params.getOrDefault("action", "UNKNOWN");
        LocalDateTime timestamp = (LocalDateTime) params.getOrDefault("timestamp", LocalDateTime.now());
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) params.getOrDefault("details", Map.of());

        return switch (type) {
            case RIDE -> {
                Long rideId = params.get("rideId") != null ? ((Number) params.get("rideId")).longValue() : null;
                yield new RideEvent(rideId, action, timestamp, details);
            }
            default -> throw new IllegalArgumentException("Unsupported event type for ride-service: " + type);
        };
    }
}
