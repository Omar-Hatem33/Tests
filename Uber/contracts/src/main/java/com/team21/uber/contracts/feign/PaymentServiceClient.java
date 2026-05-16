package com.team21.uber.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "payment-service", url = "${feign.payment-service.url}")
public interface PaymentServiceClient {

    @GetMapping("/api/payments/user/{userId}/total")
    BigDecimal getUserPaymentTotal(
            @PathVariable("userId") Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate
    );
}