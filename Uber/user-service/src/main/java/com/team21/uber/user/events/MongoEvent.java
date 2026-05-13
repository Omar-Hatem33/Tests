package com.team21.uber.user.events;

import java.time.LocalDateTime;

public interface MongoEvent {
    String getId();
    LocalDateTime getTimestamp();
    String getAction();
    Object getDetails();
}
