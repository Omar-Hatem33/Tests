package com.team21.uber.location.events;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sole construction point for MongoEvent instances in this service.
 * Primary arm LOCATION returns a LocationEvent. Other arms return a LocationEvent
 * envelope so the factory contract holds across services and callers always get a
 * valid MongoEvent.
 */
@Component
public class EventFactory {

    @SuppressWarnings("unchecked")
    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (params == null) params = Map.of();
        switch (type) {
            case LOCATION -> {
                Object driverId = numericOrRaw(params.get("driverId"));
                String action = stringOf(params.get("action"));
                Map<String, Object> details = params.containsKey("details")
                        ? (Map<String, Object>) params.get("details")
                        : params;
                if (details != null && details.containsKey("driverId")) {
                    details.put("driverId", numericOrRaw(details.get("driverId")));
                }
                return new LocationEvent(driverId, action, details);
            }
            case AUTH, DRIVER, RIDE, PAYMENT_AUDIT -> {
                // location-service only persists LOCATION events natively. Other event types are
                // accepted by the factory contract for cross-service uniformity and fall back to
                // a LocationEvent envelope so callers always get a valid MongoEvent.
                LocationEvent fallback = new LocationEvent(
                        numericOrRaw(params.get("driverId")),
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

    private Object numericOrRaw(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return o;
        if (o instanceof String s) {
            try { return Long.valueOf(s); } catch (NumberFormatException e) { return s; }
        }
        return o;
    }
}
