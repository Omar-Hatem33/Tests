package com.team21.uber.payment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "driver-service",
        contextId = "paymentDriverServiceClient",
        url = "${feign.driver-service.url}"
)
public interface DriverServiceClient {

    @GetMapping("/api/drivers/{id}")
    DriverResponse getDriver(@PathVariable("id") Long id);

    record DriverResponse(
            Long id,
            String name,
            String status,
            java.util.Map<String, Object> vehicleDetails
    ) {}
}