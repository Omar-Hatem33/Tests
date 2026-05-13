package com.team21.uber.ride.events;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface RideAnalyticsRepository {

    long countTotalRides(LocalDateTime start, LocalDateTime end);

    long countCompletedRides(LocalDateTime start, LocalDateTime end);

    double sumRevenue(LocalDateTime start, LocalDateTime end);

    List<Map<String, Object>> countByStatus(LocalDateTime start, LocalDateTime end);
}
