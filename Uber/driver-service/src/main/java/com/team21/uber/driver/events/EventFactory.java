package com.team21.uber.driver.events;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventFactory {

    @SuppressWarnings("unchecked")
    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (params == null) params = Map.of();
        switch (type) {
            case DRIVER -> {
                Long driverId = params.get("driverId") instanceof Number n
                        ? n.longValue()
                        : params.get("driverId") != null
                          ? Long.valueOf(String.valueOf(params.get("driverId")))
                          : null;
                String action = stringOf(params.get("action"));
                Map<String, Object> details = params.containsKey("details")
                        ? (Map<String, Object>) params.get("details")
                        : params;
                return new DriverEvent(driverId, action, details);
            }
            case AUTH, RIDE, LOCATION, PAYMENT_AUDIT -> {
                // driver-service only persists DRIVER events natively. Other event types are
                // accepted by the factory contract for cross-service uniformity and fall back
                // to a DriverEvent envelope so callers always get a valid MongoEvent.
                Long driverId = params.get("driverId") instanceof Number n
                        ? n.longValue()
                        : params.get("driverId") != null
                          ? Long.valueOf(String.valueOf(params.get("driverId")))
                          : null;
                DriverEvent fallback = new DriverEvent(
                        driverId,
                        stringOf(params.get("action")),
                        params);
                return fallback;
            }
            default -> throw new IllegalArgumentException("Unknown event type: " + type);
        }
    }

    private String stringOf(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
