package com.team21.uber.payment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${feign.user-service.url}")
public interface UserServiceClient {

    /**
     * Returns the user with the given id.
     * Throws FeignException.NotFound (404) if the user does not exist.
     */
    @GetMapping("/api/users/{id}")
    UserResponse getUser(@PathVariable("id") Long id);

    /** Minimal projection — only fields payment-service actually needs. */
    record UserResponse(Long id, String email, String role) {}
}