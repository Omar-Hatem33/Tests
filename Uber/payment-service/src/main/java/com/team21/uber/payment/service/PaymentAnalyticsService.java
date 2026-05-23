package com.team21.uber.payment.service;

import com.team21.uber.contracts.dto.DriverDTO;
import com.team21.uber.contracts.dto.RideDTO;
import com.team21.uber.payment.feign.DriverServiceClient;
import com.team21.uber.payment.feign.RideServiceClient;
import com.team21.uber.payment.dto.VehicleTypeRevenueDTO;
import com.team21.uber.payment.model.Payment;
import com.team21.uber.payment.model.PaymentStatus;
import com.team21.uber.payment.repository.PaymentRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaymentAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAnalyticsService.class);

    private final PaymentRepository paymentRepository;
    private final RideServiceClient rideServiceClient;
    private final DriverServiceClient driverServiceClient;

    public PaymentAnalyticsService(PaymentRepository paymentRepository,
                                   RideServiceClient rideServiceClient,
                                   DriverServiceClient driverServiceClient) {
        this.paymentRepository = paymentRepository;
        this.rideServiceClient = rideServiceClient;
        this.driverServiceClient = driverServiceClient;
    }

    public List<VehicleTypeRevenueDTO> getRevenueByVehicleType(String startDate, String endDate) {

        // 1. Fetch COMPLETED payments in date range — capped at 100 per §2.12
        LocalDateTime start = LocalDate.parse(startDate).atTime(LocalTime.MIN);
        LocalDateTime end   = LocalDate.parse(endDate).atTime(LocalTime.MAX);

        List<Payment> payments = paymentRepository
                .findCompletedInDateRange(start, end, PageRequest.of(0, 100));

        // 2. driverId → vehicleType cache (collapses N+1 for repeated drivers)
        Map<Long, String> driverVehicleCache = new HashMap<>();

        // 3. Accumulator: vehicleType → running totals
        Map<String, VehicleTypeAggregate> aggregates = new LinkedHashMap<>();

        for (Payment payment : payments) {
            Long rideId = payment.getRideId();

            // Round 1 — ride-service to get driverId
            Long driverId;
            try {
                log.info("Calling RideServiceClient.getRide with args={}", rideId);
                RideServiceClient.RideResponse ride = rideServiceClient.getRide(rideId);
                driverId = ride.driverId();
                log.info("RideServiceClient.getRide returned successfully");
            } catch (FeignException e) {
                log.warn("Feign call to ride-service failed for rideId={}: {}",
                        rideId, e.getMessage());
                continue; // skip this payment gracefully
            }

            // Round 2 — driver-service to get vehicleType (cached per driver)
            String vehicleType = driverVehicleCache.computeIfAbsent(driverId, id -> {
                try {
                    log.info("Calling DriverServiceClient.getDriver with args={}", id);
                    DriverServiceClient.DriverResponse driver = driverServiceClient.getDriver(id);
                    log.info("DriverServiceClient.getDriver returned successfully");
                    Object vt = driver.vehicleDetails() != null
                            ? driver.vehicleDetails().get("vehicleType")
                            : null;
                    return vt != null ? vt.toString() : "UNKNOWN";
                } catch (FeignException e) {
                    log.warn("Feign call to driver-service failed for driverId={}: {}",
                            id, e.getMessage());
                    return "UNKNOWN";
                }
            });

            // 4. Compute surge fee
            double amount   = payment.getAmount();
            double surgeFee = parseSurgeFee(payment, amount);
            double baseFare = amount - surgeFee;

            aggregates.computeIfAbsent(vehicleType, k -> new VehicleTypeAggregate())
                    .add(amount, baseFare, surgeFee, rideId);
        }

        // 5. Build response list
        return aggregates.entrySet().stream()
                .map(e -> VehicleTypeRevenueDTO.builder()
                        .vehicleType(e.getKey())
                        .baseFareRevenue(e.getValue().baseFareRevenue)
                        .surgeFeeRevenue(e.getValue().surgeFeeRevenue)
                        .totalRevenue(e.getValue().totalRevenue)
                        .rideCount((long) e.getValue().rideIds.size())
                        .build()
                )
                .collect(Collectors.toList());
    }

    private double parseSurgeFee(Payment payment, double amount) {
        if (payment.getTransactionDetails() != null
                && payment.getTransactionDetails().containsKey("surgeFee")) {
            Object val = payment.getTransactionDetails().get("surgeFee");
            if (val instanceof Number) return ((Number) val).doubleValue();
        }
        return amount * 0.15; // default per M2 spec §4.6
    }

    // Package-private accumulator — not exposed as a DTO
    static class VehicleTypeAggregate {
        double totalRevenue    = 0;
        double baseFareRevenue = 0;
        double surgeFeeRevenue = 0;
        Set<Long> rideIds      = new HashSet<>();

        void add(double total, double base, double surge, Long rideId) {
            totalRevenue    += total;
            baseFareRevenue += base;
            surgeFeeRevenue += surge;
            rideIds.add(rideId);
        }
    }
}