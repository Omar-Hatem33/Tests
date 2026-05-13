package com.team21.uber.ride.events;

import com.team21.uber.ride.event.RideEvent;
import com.team21.uber.ride.repository.RideEventRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class RideEventLogger {

    private final RideEventRepository rideEventRepository;

    public RideEventLogger(RideEventRepository rideEventRepository) {
        this.rideEventRepository = rideEventRepository;
    }

    public void logAnalyticsViewed(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> details = new HashMap<>();
        details.put("startDate", startDate.toString());
        details.put("endDate", endDate.toString());
        RideEvent ev = new RideEvent(null, "ANALYTICS_VIEWED", LocalDateTime.now(), details);
        try {
            rideEventRepository.save(ev);
        } catch (Exception ignored) {}
    }
}
