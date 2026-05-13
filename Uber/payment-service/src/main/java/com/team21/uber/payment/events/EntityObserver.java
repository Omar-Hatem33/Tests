package com.team21.uber.payment.events;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}