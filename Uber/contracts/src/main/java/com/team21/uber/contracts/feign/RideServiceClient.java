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

    @GetMapping("/api/rides/user/{userId}/summary")
    RideSummaryDTO getUserRideSummary(@PathVariable("userId") Long userId);

    @GetMapping("/api/rides/user/{userId}/active-count")
    int getUserActiveRideCount(@PathVariable("userId") Long userId);

    @GetMapping("/api/rides/user/{userId}/completed-count")
    long getUserCompletedRideCount(@PathVariable("userId") Long userId);

    @GetMapping("/api/rides/driver/{driverId}/summary")
    DriverRideSummaryDTO getDriverRideSummary(
            @PathVariable("driverId") Long driverId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    );

    @GetMapping("/api/rides/driver/{driverId}/active-count")
    int getDriverActiveRideCount(@PathVariable("driverId") Long driverId);

    @GetMapping("/api/rides/driver/{driverId}/completed-count")
    long getDriverCompletedRideCount(@PathVariable("driverId") Long driverId);

    @GetMapping("/api/rides/{rideId}")
    RideDTO getRide(@PathVariable("rideId") Long rideId);
}