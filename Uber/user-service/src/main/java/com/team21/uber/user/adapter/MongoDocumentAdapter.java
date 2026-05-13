package com.team21.uber.user.adapter;

import com.team21.uber.user.dto.UserActivityFeedItemDTO;
import com.team21.uber.user.events.AuthEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MongoDocumentAdapter {

    @SuppressWarnings("unchecked")
    public UserActivityFeedItemDTO adapt(AuthEvent ev) {
        if (ev == null) return null;
        Map<String, Object> details = ev.getDetails() instanceof Map
                ? (Map<String, Object>) ev.getDetails()
                : new HashMap<>();
        return UserActivityFeedItemDTO.builder()
                .eventId(ev.getId())
                .userId(ev.getUserId())
                .action(ev.getAction())
                .timestamp(ev.getTimestamp())
                .details(details)
                .build();
    }
}
