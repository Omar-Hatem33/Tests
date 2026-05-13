package com.team21.uber.payment.service;

import com.team21.uber.payment.dto.VehicleTypeRevenueDTO;
import com.team21.uber.payment.events.EventFactory;
import com.team21.uber.payment.events.EventType;
import com.team21.uber.payment.events.PaymentAuditEvent;
import com.team21.uber.payment.events.PaymentAuditEventRepository;
import com.team21.uber.payment.repository.PaymentRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final PaymentRepository           paymentRepository;
    private final PaymentAuditEventRepository auditRepository;

    public AnalyticsService(PaymentRepository paymentRepository,
                            PaymentAuditEventRepository auditRepository) {
        this.paymentRepository = paymentRepository;
        this.auditRepository   = auditRepository;
    }

    // ─── S5-F10 ──────────────────────────────────────────────────────────────
    //
    // Public entry-point called by the controller.
    // Audit logging happens HERE, outside the @Cacheable method, so it fires on
    // every request — including cache hits (spec §10.5.1-f).
    //
    public List<VehicleTypeRevenueDTO> getFareRevenueByVehicleType(LocalDate startDate,
                                                                   LocalDate endDate) {
        // (a) 400 if startDate is after endDate
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must not be after endDate");
        }

        // (f) Audit log — always written, even on cache hits
        Map<String, Object> params = new HashMap<>();
        params.put("id",        null);
        params.put("action",    "ANALYTICS_VIEWED");
        params.put("timestamp", LocalDateTime.now());
        params.put("feature",   "S5-F10");
        params.put("startDate", startDate.toString());
        params.put("endDate",   endDate.toString());

        PaymentAuditEvent auditEvent = (PaymentAuditEvent) EventFactory.createEvent(EventType.PAYMENT_AUDIT, params);
        auditRepository.save(auditEvent);

        // Delegate to the cached layer (10-min TTL via CacheConfig "S5-F10")
        return cachedFareRevenueByVehicleType(startDate, endDate);
    }

    // ─── Cached layer ─────────────────────────────────────────────────────────
    //
    // Key  : "S5-F10::<startDate>:<endDate>"
    // Cache: "S5-F10" → 10-min TTL declared in CacheConfig
    //
    @Cacheable(value = "S5-F10", key = "#startDate + ':' + #endDate")
    @Transactional(readOnly = true)
    public List<VehicleTypeRevenueDTO> cachedFareRevenueByVehicleType(LocalDate startDate,
                                                                      LocalDate endDate) {
        // (b) Expand LocalDate to fully-closed timestamp window
        LocalDateTime start = startDate.atStartOfDay();                              // T00:00:00
        LocalDateTime end   = endDate.atTime(LocalTime.of(23, 59, 59, 999_000_000)); // T23:59:59.999

        // (c,d,e) Native SQL: JOIN payments → rides → drivers, filter + group
        List<Object[]> rows = paymentRepository.findFareRevenueByVehicleType(start, end);

        // (h) Map rows → DTOs using builder; return empty list when no data
        return rows.stream()
                .map(row -> VehicleTypeRevenueDTO.builder()
                        .vehicleType    ((String) row[0])
                        .surgeFeeRevenue(row[1] != null ? ((Number) row[1]).doubleValue() : 0.0)
                        .baseFareRevenue(row[2] != null ? ((Number) row[2]).doubleValue() : 0.0)
                        .totalRevenue   (row[3] != null ? ((Number) row[3]).doubleValue() : 0.0)
                        .rideCount      (row[4] != null ? ((Number) row[4]).longValue()   : 0L)
                        .build())
                .collect(Collectors.toList());
    }
}