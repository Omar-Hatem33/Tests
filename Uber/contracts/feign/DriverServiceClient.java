package com.team21.uber.contracts.feign;

import com.team21.uber.contracts.dto.DriverAvailabilityDTO;
import com.team21.uber.contracts.dto.DriverDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "driver-service", url = "${feign.driver-service.url}")
public interface DriverServiceClient {

    // Used by S3-F2, S3-F11, S3-F12, S4-F3, S4-F9, S5-F10
    @GetMapping("/api/drivers/{id}")
    DriverDTO getDriver(@PathVariable Long id);

    // Used by S3-F2 availability pre-check
    @GetMapping("/api/drivers/{id}/availability")
    DriverAvailabilityDTO getDriverAvailability(@PathVariable Long id);
}