package com.team21.uber.location.adapter;

import com.team21.uber.location.events.LocationEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component
public class MongoDocumentAdapter {

    @SuppressWarnings("unchecked")
    public Map<String, Object> adapt(LocationEvent event) {
        Map<String, Object> out = new HashMap<>();
        if (event == null) return out;
        out.put("id", event.getId());
        out.put("driverId", event.getDriverId());
        out.put("action", event.getAction());
        out.put("timestamp", event.getTimestamp());
        Object raw = event.getDetails();
        if (raw instanceof Map) {
            out.put("details", new HashMap<>((Map<String, Object>) raw));
        } else {
            Map<String, Object> details = new HashMap<>();
            if (raw != null) details.put("value", raw);
            out.put("details", details);
        }
        return out;
    }
}
