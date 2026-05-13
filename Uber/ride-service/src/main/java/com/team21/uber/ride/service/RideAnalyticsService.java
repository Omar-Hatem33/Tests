package com.team21.uber.ride.service;

import com.team21.uber.ride.dto.RideAnalyticsDashboardDTO;
import com.team21.uber.ride.events.RideEventLogger;
import com.team21.uber.ride.events.RideAnalyticsRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.team21.uber.ride.dto.JsonUtil;

import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
public class RideAnalyticsService {

    private final RideAnalyticsRepository repo;
    private final StringRedisTemplate redis;
    private final RideEventLogger eventLogger;

    public RideAnalyticsService(RideAnalyticsRepository repo,
                                StringRedisTemplate redis,
                                RideEventLogger eventLogger) {
        this.repo = repo;
        this.redis = redis;
        this.eventLogger = eventLogger;
    }

    public RideAnalyticsDashboardDTO getDashboard(LocalDate startDate, LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        String cacheKey = "dashboard:" + startDate + ":" + endDate;
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RideAnalyticsService.class);
        log.info("DASHBOARD called start={} end={} cacheKey={}", start, end, cacheKey);

        eventLogger.logAnalyticsViewed(startDate, endDate);

        // ✅ FIX: read as JSON string
        String cachedJson = redis.opsForValue().get(cacheKey);

        if (cachedJson != null) {
            log.info("DASHBOARD cache hit: {}", cachedJson);
            RideAnalyticsDashboardDTO cached = JsonUtil.fromJson(cachedJson, RideAnalyticsDashboardDTO.class);
            if (cached != null) {
                if (Double.isNaN(cached.getTotalRevenue()) || cached.getTotalRevenue() < 0)
                    cached.setTotalRevenue(0.0);
                if (Double.isNaN(cached.getAverageRideFare()) || cached.getAverageRideFare() < 0)
                    cached.setAverageRideFare(0.0);
                if (Double.isNaN(cached.getCompletionRate()) || cached.getCompletionRate() < 0)
                    cached.setCompletionRate(0.0);
                return cached;
            }
        }

        long totalRides = Math.max(0, repo.countTotalRides(start, end));
        long completedRides = Math.max(0, repo.countCompletedRides(start, end));
        double totalRevenue;
        try {
            totalRevenue = repo.sumRevenue(start, end);
        } catch (Exception ex) {
            totalRevenue = 0.0;
        }
        if (Double.isNaN(totalRevenue) || totalRevenue < 0) totalRevenue = 0.0;

        double avgFare = completedRides > 0 ? totalRevenue / completedRides : 0.0;
        double completionRate = totalRides > 0 ? (double) completedRides / totalRides : 0.0;

        Map<String, Long> statusMap = new HashMap<>();
        repo.countByStatus(start, end).forEach(row ->
                statusMap.put(
                        (String) row.get("status"),
                        ((Number) row.get("count")).longValue()
                )
        );

        RideAnalyticsDashboardDTO dto = new RideAnalyticsDashboardDTO(
                totalRides,
                totalRevenue,
                avgFare,
                completionRate,
                statusMap
        );
        log.info("DASHBOARD computed totalRides={} totalRevenue={} avg={} rate={} statusMap={}",
                totalRides, totalRevenue, avgFare, completionRate, statusMap);

        // ✅ FIX: store as JSON
        redis.opsForValue().set(
                cacheKey,
                JsonUtil.toJson(dto),
                10,
                TimeUnit.MINUTES
        );

        return dto;
    }
}