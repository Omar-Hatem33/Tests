package com.team21.uber.ride.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}

