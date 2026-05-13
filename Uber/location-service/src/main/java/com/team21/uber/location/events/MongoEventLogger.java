package com.team21.uber.location.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Concrete observer that persists location events to MongoDB.
 * Soft-fails when Mongo is unavailable: no rethrow, just WARN log.
 */
@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final EventFactory eventFactory;
    private final ObjectProvider<LocationEventRepository> repoProvider;

    public MongoEventLogger(EventFactory eventFactory,
                            ObjectProvider<LocationEventRepository> repoProvider) {
        this.eventFactory = eventFactory;
        this.repoProvider = repoProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEvent(String eventType, Object payload) {
        try {
            LocationEventRepository repo = repoProvider.getIfAvailable();
            if (repo == null) {
                log.debug("Mongo not available; skipping event {}", eventType);
                return;
            }
            Map<String, Object> params = payload instanceof Map
                    ? new HashMap<>((Map<String, Object>) payload)
                    : new HashMap<>();
            EventType type = mapEventType(eventType, params);
            MongoEvent ev = eventFactory.createEvent(type, params);
            if (ev instanceof LocationEvent le) {
                repo.save(le);
            }
        } catch (Exception ex) {
            log.warn("MongoEventLogger failed to persist event {}: {}", eventType, ex.getMessage());
        }
    }

    private EventType mapEventType(String eventType, Map<String, Object> params) {
        if (!params.containsKey("action")) {
            params.put("action", eventType);
        }
        return EventType.LOCATION;
    }
}
