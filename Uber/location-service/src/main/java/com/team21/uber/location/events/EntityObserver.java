package com.team21.uber.location.events;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
