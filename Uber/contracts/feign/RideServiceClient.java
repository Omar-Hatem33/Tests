package com.team21.uber.contracts.feign;

import com.team21.uber.contracts.dto.DriverRideSummaryDTO;
import com.team21.uber.contracts.dto.RideDTO;
import com.team21.uber.contracts.dto.RideSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ride-service", url = "${feign.ride-service.url}")
public interface RideServiceClient {

    // Used by S1-F3
    @GetMapping("/api/rides/user/{userId}/summary")
    RideSummaryDTO getUserRideSummary(@PathVariable Long userId);

    // Used by S1-F4
    @GetMapping("/api/rides/user/{userId}/active-count")
    int getUserActiveRideCount(@PathVariable Long userId);

    // Used by S1-F9
    @GetMapping("/api/rides/user/{userId}/completed-count")
    long getUserCompletedRideCount(@PathVariable Long userId);

    // Used by S2-F3 and S2-F12 (dates are optional — pass null to get all-time)
    @GetMapping("/api/rides/driver/{driverId}/summary")
    DriverRideSummaryDTO getDriverRideSummary(
            @PathVariable Long driverId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    );

    // Used by S2-F4
    @GetMapping("/api/rides/driver/{driverId}/active-count")
    int getDriverActiveRideCount(@PathVariable Long driverId);

    // Used by S2-F6
    @GetMapping("/api/rides/driver/{driverId}/completed-count")
    long getDriverCompletedRideCount(@PathVariable Long driverId);

    // Used by S2-F7, S5-F4, S5-F10
    @GetMapping("/api/rides/{rideId}")
    RideDTO getRide(@PathVariable Long rideId);
}