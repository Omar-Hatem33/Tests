package com.team21.uber.payment.service;

import com.team21.uber.payment.dto.PaymentMethodBreakdownDTO;
import com.team21.uber.payment.events.PaymentAuditEvent;
import com.team21.uber.payment.events.PaymentAuditEventRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentMethodBreakdownService {

    private final PaymentAuditEventRepository auditEventRepository;

    public PaymentMethodBreakdownService(
            PaymentAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Cacheable(value = "S5-F11", key = "#startDate.toString() + ':' + #endDate.toString()")
    public List<PaymentMethodBreakdownDTO> getMethodBreakdown(
            LocalDate startDate, LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must not be after endDate");
        }

        LocalDateTime start = startDate.atTime(LocalTime.MIN);
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<PaymentAuditEvent> events =
                auditEventRepository.findCompletedAndFailedByTimestampBetween(
                        start, end);

        Map<String, Long> successCounts = new HashMap<>();
        Map<String, Long> failureCounts = new HashMap<>();
        Map<String, Double> totalAmounts = new HashMap<>();

        for (PaymentAuditEvent event : events) {
            String method = event.getMethod();
            if (method == null) continue;

            if ("COMPLETED".equals(event.getAction())) {
                successCounts.merge(method, 1L, Long::sum);
                Double amount = event.getAmount();
                if (amount != null) {
                    totalAmounts.merge(method, amount, Double::sum);
                }
            } else if ("FAILED".equals(event.getAction())) {
                failureCounts.merge(method, 1L, Long::sum);
            }
        }

        Map<String, Boolean> allMethods = new HashMap<>();
        successCounts.keySet().forEach(m -> allMethods.put(m, true));
        failureCounts.keySet().forEach(m -> allMethods.put(m, true));

        List<PaymentMethodBreakdownDTO> result = new ArrayList<>();

        for (String method : allMethods.keySet()) {
            long success = successCounts.getOrDefault(method, 0L);
            long failure = failureCounts.getOrDefault(method, 0L);
            long total = success + failure;
            double rate = total > 0 ? (double) success / total : 0.0;
            double amount = totalAmounts.getOrDefault(method, 0.0);

            result.add(PaymentMethodBreakdownDTO.builder()
                    .method(method)
                    .successCount(success)
                    .failureCount(failure)
                    .successRate(rate)
                    .totalAmount(amount)
                    .build());
        }

        return result;
    }
}