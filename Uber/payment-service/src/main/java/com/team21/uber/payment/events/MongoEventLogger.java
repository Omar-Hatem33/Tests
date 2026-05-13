package com.team21.uber.payment.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);
    private final PaymentAuditEventRepository repository;

    public MongoEventLogger(PaymentAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        System.out.println("MongoEventLogger received event: " + eventType);
        try {
            if (payload instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = (Map<String, Object>) rawMap;

                // 1. Unpack fields safely with fallbacks
                Long paymentId = map.get("paymentId") != null ? ((Number) map.get("paymentId")).longValue() : null;
                String method = map.get("method") != null ? map.get("method").toString() : null;
                Double amount = map.get("amount") != null ? ((Number) map.get("amount")).doubleValue() : null;

                // 2. Parse the historical setup timestamp
                LocalDateTime eventTimestamp = LocalDateTime.now();
                if (map.containsKey("createdAt") && map.get("createdAt") != null) {
                    eventTimestamp = LocalDateTime.parse(map.get("createdAt").toString());
                }

                // 3. Separate remaining fields into details map
                Map<String, Object> details = new HashMap<>(map);
                details.remove("paymentId");
                details.remove("method");
                details.remove("amount");
                details.remove("createdAt");

                // 4. Instantiate the entity DIRECTLY using its constructor
                PaymentAuditEvent auditEvent = new PaymentAuditEvent(
                        paymentId,
                        eventType, // e.g. "COMPLETED" or "FAILED"
                        eventTimestamp,
                        method,
                        amount,
                        details
                );

                // 5. Save to MongoDB
                PaymentAuditEvent savedEvent = repository.save(auditEvent);
                System.out.println("SUCCESS: Saved to Mongo. ID: " + savedEvent.getId() + ", Action: " + savedEvent.getAction() + ", Date: " + savedEvent.getTimestamp());
            } else {
                System.out.println("MongoEventLogger skipped: Payload was not a Map structure.");
            }

        } catch (Exception e) {
            System.err.println("CRITICAL FAILURE inside MongoEventLogger: " + e.getMessage());
            e.printStackTrace(); // Forces the full stack trace out to standard error console
        }
    }
}