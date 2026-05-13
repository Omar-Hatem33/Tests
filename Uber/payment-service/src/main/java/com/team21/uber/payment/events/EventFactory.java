package com.team21.uber.payment.events;

import java.time.LocalDateTime;
import java.util.Map;

public class EventFactory {

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (type == null)   throw new IllegalArgumentException("EventType must not be null");
        if (params == null) throw new IllegalArgumentException("params must not be null");

        return switch (type) {

            case PAYMENT_AUDIT -> buildPaymentAuditEvent(params);
            case AUTH, DRIVER, RIDE, LOCATION -> buildFallbackEvent(type, params);
        };
    }

    private static PaymentAuditEvent buildPaymentAuditEvent(Map<String, Object> params) {
        String action    = getString(params, "action", "UNKNOWN");
        Long   paymentId = getLong(params,   "paymentId");
        String method    = getString(params, "method",   null);
        Double amount    = getDouble(params, "amount",   null);

        @SuppressWarnings("unchecked")
        Map<String, Object> details =
                params.containsKey("details")
                        ? (Map<String, Object>) params.get("details")
                        : Map.of();

        LocalDateTime ts = params.containsKey("timestamp")
                ? (LocalDateTime) params.get("timestamp")
                : LocalDateTime.now();

        boolean isLifecycleAction = isPaymentLifecycleAction(action);
        if (isLifecycleAction && (method == null || amount == null)) {
            throw new IllegalArgumentException(
                    "method and amount are required for action: " + action);
        }

        if (isLifecycleAction) {
            return new PaymentAuditEvent(paymentId, action, ts, method, amount, details);
        } else {
            return new PaymentAuditEvent(paymentId, action, ts, details);
        }
    }

    private static boolean isPaymentLifecycleAction(String action) {
        return switch (action) {
            case "CREATED", "COMPLETED", "FAILED",
                 "REFUNDED", "REFUND_DENIED",
                 "COUPON_APPLIED", "RETRY_ATTEMPTED",
                 "PAYMENT_DELETED" -> true;
            default -> false;
        };
    }

    private static PaymentAuditEvent buildFallbackEvent(EventType type,
                                                        Map<String, Object> params) {
        String action = getString(params, "action", type.name() + "_EVENT");
        return new PaymentAuditEvent(null, action, LocalDateTime.now(), Map.of());
    }

    private static String getString(Map<String, Object> p, String key, String fallback) {
        Object v = p.get(key);
        return v != null ? v.toString() : fallback;
    }

    private static Long getLong(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.valueOf(v.toString());
    }

    private static Double getDouble(Map<String, Object> p, String key, Double fallback) {
        Object v = p.get(key);
        if (v == null) return fallback;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.valueOf(v.toString()); } catch (NumberFormatException e) { return fallback; }
    }
}