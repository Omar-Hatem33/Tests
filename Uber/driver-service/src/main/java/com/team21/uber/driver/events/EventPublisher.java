package com.team21.uber.driver.events;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publishes events to registered observers.
 * Auto-registers all Spring-managed observers at @PostConstruct so business code can call
 * notifyObservers(...) without coupling to specific observer implementations.
 */
@Component
public class EventPublisher {

    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
    private final ObjectProvider<EntityObserver> autoRegistered;

    public EventPublisher(ObjectProvider<EntityObserver> autoRegistered) {
        this.autoRegistered = autoRegistered;
    }

    @PostConstruct
    public void init() {
        autoRegistered.orderedStream().forEach(this::register);
    }

    public void register(EntityObserver o) {
        if (o != null && !observers.contains(o)) observers.add(o);
    }

    public void unregister(EntityObserver o) {
        observers.remove(o);
    }

    public void unregisterAll() {
        observers.clear();
    }

    public void notifyObservers(String eventType, Object payload) {
        for (EntityObserver o : observers) {
            o.onEvent(eventType, payload);
        }
    }

    public List<EntityObserver> getObservers() {
        return List.copyOf(observers);
    }
}
