package com.team21.uber.user.events;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
