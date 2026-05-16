package com.team21.uber.payment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ride-service", url = "${feign.ride-service.url}")
public interface RideServiceClient {

    @GetMapping("/api/rides/{id}")
    RideResponse getRide(@PathVariable("id") Long id);

    record RideResponse(
            Long id,
            Long userId,
            Long driverId,
            Double amount,
            String status
    ) {}
}