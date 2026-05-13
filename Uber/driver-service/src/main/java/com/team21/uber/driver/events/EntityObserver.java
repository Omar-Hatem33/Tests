package com.team21.uber.driver.events;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
