package com.team21.uber.contracts.feign;

import com.team21.uber.contracts.dto.LocationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "location-service", url = "${feign.location-service.url}")
public interface LocationServiceClient {

    // Used by S3-F4 saga pre-check (returns 404 if no ping within last 5 minutes)
    @GetMapping("/api/locations/driver/{driverId}/recent")
    LocationDTO getRecentLocationForDriver(@PathVariable Long driverId);
}