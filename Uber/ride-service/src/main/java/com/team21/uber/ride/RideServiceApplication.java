package com.team21.uber.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = {"com.team21.uber.contracts.feign", "com.team21.uber.ride"})
public class RideServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}

