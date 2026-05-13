package com.team21.uber.payment.controller;

import com.team21.uber.payment.dto.*;
import com.team21.uber.payment.dto.PaymentDetailsDTO;
import com.team21.uber.payment.service.CouponService;
import com.team21.uber.payment.dto.PaymentRequestDTO;
import com.team21.uber.payment.dto.RevenueReportDTO;
import com.team21.uber.payment.dto.PaymentDetailsDTO;
import com.team21.uber.payment.dto.UserPaymentSummaryDTO;
import com.team21.uber.payment.service.PaymentMethodBreakdownService;
import com.team21.uber.payment.service.PaymentService;
import com.team21.uber.payment.model.Payment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CouponService couponService;
    private final PaymentMethodBreakdownService paymentMethodBreakdownService;

    public PaymentController(PaymentService paymentService, CouponService couponService, PaymentMethodBreakdownService paymentMethodBreakdownService) {
        this.paymentService = paymentService;
        this.couponService = couponService;
        this.paymentMethodBreakdownService = paymentMethodBreakdownService;
    }

    @PostMapping
    public ResponseEntity<Payment> create(@RequestBody Payment payment) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping
    public ResponseEntity<List<Payment>> getAll() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Payment> update(@PathVariable Long id, @RequestBody Payment payment) {
        return ResponseEntity.ok(paymentService.updatePayment(id, payment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/{paymentId}/details")
    public ResponseEntity<PaymentDetailsDTO> getPaymentDetails(@PathVariable Long paymentId) {
        PaymentDetailsDTO details = paymentService.getPaymentDetails(paymentId);
        return ResponseEntity.ok(details);
    }
  
    @PutMapping("/{id}/retry")
    public ResponseEntity<Payment> retryPayment(@PathVariable Long id) {
        Payment updatedPayment = paymentService.retryPayment(id);
        return ResponseEntity.ok(updatedPayment);
    }

    // GET /api/payments/reports/revenue?startDate=...&endDate=...
    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDateTime start = parseDateTime(startDate, LocalTime.MIN);
        LocalDateTime end = parseDateTime(endDate, LocalTime.MAX);
        return ResponseEntity.ok(paymentService.getRevenueReport(start, end));
    }

    @PostMapping("/{paymentId}/coupons/{couponId}")
    public ResponseEntity<PaymentDetailsDTO> applyCoupon(
            @PathVariable Long paymentId,
            @PathVariable Long couponId) {
        PaymentDetailsDTO response = paymentService.applyCoupon(paymentId, couponId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserPaymentSummaryDTO> userSummary(@PathVariable Long userId){
        return ResponseEntity.ok(paymentService.getUserPaymentSummary(userId));
    }
  
    @PutMapping("/{id}/refund")
    public ResponseEntity<Payment> refund(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(paymentService.processRefund(id, body.get("reason")));
    }

    private LocalDateTime parseDateTime(String date, LocalTime defaultTime) {
        try {
            return LocalDateTime.parse(date);
        } catch (Exception e) {
            try {
                return LocalDate.parse(date).atTime(defaultTime);
            } catch (Exception e2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid date format: " + date);
            }
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Payment>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long userId) {

        LocalDateTime start = (startDate != null)
                ? parseDateTime(startDate, LocalTime.MIN)
                : null;

        LocalDateTime end = (endDate != null)
                ? parseDateTime(endDate, LocalTime.MAX)
                : null;

        return ResponseEntity.ok(paymentService.searchPayments(status, start, end, userId));
    }

    //F4
    @PostMapping("/ride/{rideId}")
    public ResponseEntity<PaymentDetailsDTO> processPaymentForRide(
            @PathVariable Long rideId,
            @RequestBody PaymentRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.processPaymentForRide(rideId, request));
    }

    @GetMapping("/coupons/top-used")
    public ResponseEntity<List<CouponUsageDTO>> getTopUsedCoupons(
            @RequestParam(defaultValue = "10") int limit) {
        List<CouponUsageDTO> topCoupons = couponService.getTopUsedCoupons(limit);
        return ResponseEntity.ok(topCoupons);
    }

    //F11
    @GetMapping("/analytics/methods")
    public ResponseEntity<List<PaymentMethodBreakdownDTO>> getMethodBreakdown(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return ResponseEntity.ok(
                paymentMethodBreakdownService.getMethodBreakdown(start, end));
    }
    @PostMapping("/{id}/refund-surge-adjusted")
    public ResponseEntity<RefundResponseDTO> refundSurgeAdjusted(
            @PathVariable Long id,
            @RequestBody RefundRequestDTO request) {
        RefundResponseDTO response = paymentService.refundSurgeAdjusted(id, request);
        return ResponseEntity.ok(response);
    }

}
