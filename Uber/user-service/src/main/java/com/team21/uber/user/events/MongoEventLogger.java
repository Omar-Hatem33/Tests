package com.team21.uber.user.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Concrete observer that persists events to MongoDB.
 * Soft-fails when Mongo is unavailable: no rethrow, just WARN log.
 */
@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final EventFactory eventFactory;
    private final ObjectProvider<AuthEventRepository> repoProvider;

    public MongoEventLogger(EventFactory eventFactory,
                            ObjectProvider<AuthEventRepository> repoProvider) {
        this.eventFactory = eventFactory;
        this.repoProvider = repoProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEvent(String eventType, Object payload) {
        try {
            AuthEventRepository repo = repoProvider.getIfAvailable();
            if (repo == null) {
                log.debug("Mongo not available; skipping event {}", eventType);
                return;
            }
            Map<String, Object> params = payload instanceof Map
                    ? (Map<String, Object>) payload
                    : new HashMap<>();

            // Ensure action is populated for the factory
            if (!params.containsKey("action")) {
                params = new HashMap<>(params);
                params.put("action", eventType);
            }

            EventType type = EventType.AUTH;
            MongoEvent ev = eventFactory.createEvent(type, params);
            // user-service EventFactory always returns AuthEvent for AUTH type
            repo.save((AuthEvent) ev);
        } catch (Exception ex) {
            log.warn("MongoEventLogger failed to persist event {}: {}", eventType, ex.getMessage());
        }
    }
}