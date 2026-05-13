package com.team21.uber.payment.events;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EventPublisher {

    private final List<EntityObserver> observers = new ArrayList<>();

    public EventPublisher(MongoEventLogger mongoEventLogger) {
        observers.add(mongoEventLogger);
    }

    public void register(EntityObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }
}