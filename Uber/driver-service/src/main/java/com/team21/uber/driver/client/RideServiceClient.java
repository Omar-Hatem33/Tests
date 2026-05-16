package com.team21.uber.driver.client;

import com.team21.uber.contracts.dto.DriverRideSummaryDTO;
import com.team21.uber.driver.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for ride-service.
 * Used by S2-F4 (active ride check) and S2-F7 (ride validation).
 */
@FeignClient(name = "ride-service", url = "${feign.ride-service.url}")
public interface RideServiceClient {

    /**
     * Get a ride by ID. Used by S2-F7 to validate ride exists and belongs to driver.
     * Throws FeignException.NotFound (404) if ride doesn't exist.
     */
    @GetMapping("/api/rides/{rideId}")
    RideDTO getRide(@PathVariable Long rideId);

    /**
     * Count active rides for a driver. Used by S2-F4 to check before going OFFLINE.
     * Active = ACCEPTED or IN_PROGRESS.
     */
    @GetMapping("/api/rides/driver/{driverId}/active-count")
    int getActiveRideCount(@PathVariable Long driverId);

    // used for S2-F3
    @GetMapping("/api/rides/driver/{driverId}/summary")
    DriverRideSummaryDTO getDriverRideSummary(
        @PathVariable Long driverId,
        @RequestParam String startDate,
        @RequestParam String endDate
    );
}
