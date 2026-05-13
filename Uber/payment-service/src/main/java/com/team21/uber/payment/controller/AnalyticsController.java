package com.team21.uber.payment.controller;

import com.team21.uber.payment.dto.VehicleTypeRevenueDTO;
import com.team21.uber.payment.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/payments/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * S5-F10 – GET /api/payments/analytics/vehicle-type
     *
     * Query params:
     *   startDate  LocalDate (ISO, e.g. 2024-01-01)  required
     *   endDate    LocalDate (ISO, e.g. 2024-03-31)  required
     *
     * Auth : JWT required (enforced by JwtAuthenticationFilter — any valid token)
     * Cache: 10 min via @Cacheable("S5-F10") in AnalyticsService
     * Audit: ANALYTICS_VIEWED written to MongoDB on every call (incl. cache hits)
     *
     * Responses:
     *   200  List<VehicleTypeRevenueDTO>  (empty list if no data)
     *   400  startDate after endDate
     *   401  missing / invalid JWT  (thrown by JwtAuthenticationFilter)
     */
    @GetMapping("/vehicle-type")
    public ResponseEntity<List<VehicleTypeRevenueDTO>> getFareRevenueByVehicleType(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<VehicleTypeRevenueDTO> result =
                analyticsService.getFareRevenueByVehicleType(startDate, endDate);

        return ResponseEntity.ok(result);
    }
}
