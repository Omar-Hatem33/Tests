package com.team21.uber.user.events;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventFactory {

    @SuppressWarnings("unchecked")
    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (params == null) params = Map.of();
        switch (type) {
            case AUTH -> {
                // userId must be Long per spec §7.1.2
                Long userId = longOf(params.get("userId"));
                String action = stringOf(params.get("action"));
                Map<String, Object> details = params.containsKey("details")
                        ? (Map<String, Object>) params.get("details")
                        : params;
                return new AuthEvent(userId, action, details);
            }
            case DRIVER, RIDE, LOCATION, PAYMENT_AUDIT -> {
                // user-service only persists AUTH events natively.
                // Other types accepted for cross-service uniformity.
                return new AuthEvent(longOf(params.get("userId")),
                        stringOf(params.get("action")),
                        params);
            }
            default -> throw new IllegalArgumentException("Unknown event type: " + type);
        }
    }

    private Long longOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    private String stringOf(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}