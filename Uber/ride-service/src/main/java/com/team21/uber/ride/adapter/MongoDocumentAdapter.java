package com.team21.uber.ride.adapter;

import com.team21.uber.ride.event.RideEvent;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

public class MongoDocumentAdapter {

    @SuppressWarnings("unchecked")
    public RideEvent adapt(Document document) {
        RideEvent event = new RideEvent();
        event.setId(document.getObjectId("_id") != null ? document.getObjectId("_id").toString() : null);
        event.setRideId(document.getLong("rideId"));
        event.setAction(document.getString("action"));
        Date ts = document.getDate("timestamp");
        if (ts != null) {
            event.setTimestamp(ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        event.setDetails((Map<String, Object>) document.get("details"));
        return event;
    }
}
