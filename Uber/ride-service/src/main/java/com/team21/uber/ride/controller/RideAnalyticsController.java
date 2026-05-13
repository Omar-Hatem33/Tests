package com.team21.uber.ride.controller;

import com.team21.uber.ride.dto.RideAnalyticsDashboardDTO;
import com.team21.uber.ride.service.RideAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/rides/analytics")
public class RideAnalyticsController {

    private final RideAnalyticsService service;

    public RideAnalyticsController(RideAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public RideAnalyticsDashboardDTO getDashboard(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        LocalDate start = parseDate(startDate);
        LocalDate end   = parseDate(endDate);
        return service.getDashboard(start, end);
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        String trimmed = s.length() >= 10 ? s.substring(0, 10) : s;
        try {
            return LocalDate.parse(trimmed);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid date: " + s);
        }
    }
}