package com.team21.uber.payment.service;

import com.team21.uber.contracts.dto.RideDTO;
import com.team21.uber.contracts.events.PaymentCompletedEvent;
import com.team21.uber.contracts.events.PaymentFailedEvent;
import com.team21.uber.payment.feign.RideServiceClient;
import com.team21.uber.payment.dto.*;
import com.team21.uber.payment.cache.CacheInvalidator;
import com.team21.uber.payment.feign.UserServiceClient;
import com.team21.uber.payment.messaging.publishers.PaymentEventPublisher;
import com.team21.uber.payment.model.Payment;
import com.team21.uber.payment.model.PaymentCoupon;
import com.team21.uber.payment.dto.AppliedCouponDTO;
import com.team21.uber.payment.dto.PaymentDetailsDTO;
import com.team21.uber.payment.model.*;
import com.team21.uber.payment.model.PaymentStatus;
import com.team21.uber.payment.events.EventPublisher;
import com.team21.uber.payment.repository.CouponRepository;
import com.team21.uber.payment.repository.PaymentCouponRepository;
import com.team21.uber.payment.repository.PaymentRepository;
import feign.FeignException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CouponRepository couponRepository;
    private final PaymentCouponRepository paymentCouponRepository;
    private final EventPublisher eventPublisher;
    private final CacheInvalidator cacheInvalidator;
    private final UserServiceClient userServiceClient;
    private final RideServiceClient rideServiceClient;
    private final PaymentEventPublisher paymentEventPublisher;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentEventPublisher.class);

    public PaymentService(PaymentRepository paymentRepository,
                          CouponRepository couponRepository,
                          PaymentCouponRepository paymentCouponRepository,
                          EventPublisher eventPublisher,
                          CacheInvalidator cacheInvalidator,
                          UserServiceClient userServiceClient,
                          RideServiceClient rideServiceClient,
                          PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.couponRepository = couponRepository;
        this.paymentCouponRepository = paymentCouponRepository;
        this.eventPublisher = eventPublisher;
        this.cacheInvalidator = cacheInvalidator;
        this.userServiceClient=userServiceClient;
        this.rideServiceClient = rideServiceClient;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    public Payment createPayment(Payment payment) {
        if (payment.getStatus() == null) {
            payment.setStatus(PaymentStatus.PENDING);
        }
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(LocalDateTime.now());
        }
        if (payment.getTransactionDetails() == null) {
            payment.setTransactionDetails(new HashMap<>());
        }
        if (payment.getPaymentCoupons() == null) {
            payment.setPaymentCoupons(new ArrayList<>());
        }
        Payment saved = paymentRepository.save(payment);
        if (saved.getMethod() != null && saved.getAmount() != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("paymentId", saved.getId());
            payload.put("method", saved.getMethod().name());
            payload.put("amount", saved.getAmount());
            String action = saved.getStatus() != null ? saved.getStatus().name() : "CREATED";
            eventPublisher.notifyObservers(action, payload);
        }
        return saved;
    }

    @Cacheable(value = "payment", key = "#id")
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment updatePayment(Long id, Payment updated) {
        Payment existing = getPaymentById(id);
        if (updated.getAmount() != null) {
            existing.setAmount(updated.getAmount());
        }
        if (updated.getMethod() != null) {
            existing.setMethod(updated.getMethod());
        }
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }
        if (updated.getTransactionDetails() != null) {
            existing.setTransactionDetails(updated.getTransactionDetails());
        }
        Payment saved = paymentRepository.save(existing);
        invalidatePaymentCaches(id);
        return saved;
    }

    public void deletePayment(Long id) {
        Payment payment = getPaymentById(id);
        paymentRepository.deleteById(id);
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("method", payment.getMethod() != null ? payment.getMethod().name() : null);
        payload.put("amount", payment.getAmount());
        eventPublisher.notifyObservers("PAYMENT_DELETED", payload);
        invalidatePaymentCaches(id);
    }

    @Cacheable(value = "S5-F1", key = "T(java.util.Objects).toString(#status,'null') + ':' + T(java.util.Objects).toString(#start,'null') + ':' + T(java.util.Objects).toString(#end,'null') + ':' + T(java.util.Objects).toString(#userId,'null')")
    public List<Payment> searchPayments(String status, LocalDateTime start, LocalDateTime end, Long userId) {
        return paymentRepository.searchByStatusAndDateRange(status, start, end, userId);
    }

    @CacheEvict(value = "S5-F11", allEntries = true)
    @Transactional
    public Payment processRefund(Long id, String reason) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only COMPLETED payments can be refunded");
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundReason", reason);
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);
        Payment saved = paymentRepository.save(payment);
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", saved.getId());
        payload.put("method", saved.getMethod() != null ? saved.getMethod().name() : null);
        payload.put("amount", saved.getAmount());
        payload.put("refundReason", reason);
        eventPublisher.notifyObservers("REFUNDED", payload);
        invalidatePaymentCaches(id);
        return saved;
    }

    // S5-F3  — REPLACED getUserPaymentSummary()
    @Cacheable(value = "S5-F3", key = "#userId")
    public UserPaymentSummaryDTO getUserPaymentSummary(Long userId) {
        // ── Feign: verify user exists ────────────────────────────────────────────
        try {
            userServiceClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        } catch (FeignException e) {
            boolean hasPayments = !paymentRepository
                    .getCompletedPaymentsByMethod(userId).isEmpty();
            if (!hasPayments) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            log.warn("user-service call failed for userId={}, proceeding: {}", userId, e.getMessage());
        }

        // ── Build breakdown from local payments table ────────────────────────────
        List<Object[]> rows = paymentRepository.getCompletedPaymentsByMethod(userId);

        if (rows.isEmpty()) {
            // User exists but has no COMPLETED payments — return empty summary (200)
            return new UserPaymentSummaryDTO(userId, 0, 0.0, new LinkedHashMap<>());
        }

        Map<String, Double> methodBreakdown = new LinkedHashMap<>();
        int totalPayment = 0;
        double totalAmount = 0.0;

        for (Object[] row : rows) {
            String method = (String) row[0];
            long count    = ((Number) row[1]).longValue();
            double sum    = ((Number) row[2]).doubleValue();
            methodBreakdown.put(method, sum);
            totalPayment += count;
            totalAmount  += sum;
        }

        return new UserPaymentSummaryDTO(userId, totalPayment, totalAmount, methodBreakdown);
    }
//    @Cacheable(value = "S5-F3", key = "#userId")
//    public UserPaymentSummaryDTO getUserPaymentSummary(Long userId) {
//        if (paymentRepository.countUsersById(userId) == 0) {
//            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
//        }
//        List<Object[]> rows = paymentRepository.getCompletedPaymentsByMethod(userId);
//        Map<String, Double> methodBreakdown = new LinkedHashMap<>();
//        int totalPayment = 0;
//        double totalAmount = 0.0;
//        for (Object[] row : rows) {
//            String method = (String) row[0];
//            long count = ((Number) row[1]).longValue();
//            double sum = ((Number) row[2]).doubleValue();
//            methodBreakdown.put(method, sum);
//            totalPayment += count;
//            totalAmount += sum;
//        }
//        return new UserPaymentSummaryDTO(userId, totalPayment, totalAmount, methodBreakdown);
//    }

    @Transactional
    public PaymentDetailsDTO applyCoupon(Long paymentId, Long couponId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with id: " + paymentId));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot apply coupon to a completed/cancelled payment");
        }
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found with id: " + couponId));
        if (!coupon.getActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon is not active");
        }
        if (coupon.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon has expired");
        }
        if (coupon.getCurrentUses() >= coupon.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon usage limit reached");
        }
        if (paymentCouponRepository.existsByPaymentIdAndCouponId(paymentId, couponId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon already applied");
        }
        double discount;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = payment.getAmount() * coupon.getDiscountValue() / 100.0;
        } else {
            discount = coupon.getDiscountValue();
        }
        if (discount > payment.getAmount()) {
            discount = payment.getAmount();
        }
        PaymentCoupon paymentCoupon = new PaymentCoupon();
        paymentCoupon.setPayment(payment);
        paymentCoupon.setCoupon(coupon);
        paymentCoupon.setDiscountApplied(discount);
        paymentCoupon.setAppliedAt(LocalDateTime.now());
        coupon.setCurrentUses(coupon.getCurrentUses() + 1);
        couponRepository.save(coupon);
        paymentCouponRepository.save(paymentCoupon);
        couponRepository.save(coupon);
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("method", payment.getMethod() != null ? payment.getMethod().name() : null);
        payload.put("amount", payment.getAmount());
        payload.put("couponId", couponId);
        payload.put("couponCode", coupon.getCode());
        payload.put("discountApplied", discount);
        eventPublisher.notifyObservers("COUPON_APPLIED", payload);
        invalidatePaymentCaches(paymentId);
        cacheInvalidator.evictPattern("payment-service::coupon::" + couponId);
        cacheInvalidator.evictPattern("payment-service::payment-coupon::*");
        Payment updated = paymentRepository.findById(paymentId).get();
        return toDTO(updated);
    }

    @Cacheable(value = "S5-F6", key = "#startDate + ':' + #endDate")
    public RevenueReportDTO getRevenueReport(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }
        Double totalRevenue = paymentRepository.sumAmountByStatusAndDateRange(PaymentStatus.COMPLETED, startDate, endDate);
        Long totalTransactions = paymentRepository.countByStatusAndDateRange(PaymentStatus.COMPLETED, startDate, endDate);
        Double refundedAmount = paymentRepository.sumAmountByStatusAndDateRange(PaymentStatus.REFUNDED, startDate, endDate);
        Long refundCount = paymentRepository.countByStatusAndDateRange(PaymentStatus.REFUNDED, startDate, endDate);
        totalRevenue = (totalRevenue == null) ? 0.0 : totalRevenue;
        totalTransactions = (totalTransactions == null) ? 0L : totalTransactions;
        refundedAmount = (refundedAmount == null) ? 0.0 : refundedAmount;
        refundCount = (refundCount == null) ? 0L : refundCount;
        Double averagePayment = totalTransactions == 0 ? 0.0 : totalRevenue / totalTransactions;
        return new RevenueReportDTO(totalRevenue, totalTransactions, averagePayment, refundedAmount, refundCount);
    }

    @Transactional
    public RefundResponseDTO refundSurgeAdjusted(Long paymentId, RefundRequestDTO requestDTO) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only COMPLETED payments can be refunded. Current status: " + payment.getStatus());
        }
        com.team21.uber.payment.strategy.RefundRequest strategyRequest =
                new com.team21.uber.payment.strategy.RefundRequest(
                        requestDTO.getReason(), requestDTO.isRefundSurge());
        com.team21.uber.payment.strategy.RefundStrategySelector selector =
                new com.team21.uber.payment.strategy.RefundStrategySelector();
        com.team21.uber.payment.strategy.RefundStrategy strategy =
                selector.select(payment, strategyRequest);
        String strategyName = strategy.getClass().getSimpleName();
        if (strategy instanceof com.team21.uber.payment.strategy.NoRefundStrategy) {
            Map<String, Object> deniedPayload = new HashMap<>();
            deniedPayload.put("paymentId", payment.getId());
            deniedPayload.put("method", payment.getMethod() != null ? payment.getMethod().name() : null);
            deniedPayload.put("amount", payment.getAmount());
            deniedPayload.put("strategyName", strategyName);
            deniedPayload.put("refundReason", requestDTO.getReason());
            deniedPayload.put("denialReason", "refund window expired");
            eventPublisher.notifyObservers("REFUND_DENIED", deniedPayload);
            cacheInvalidator.evictPattern("payment-service::S5-F10::*");
            cacheInvalidator.evictPattern("payment-service::S5-F11::*");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    com.team21.uber.payment.strategy.NoRefundStrategy.REASON_MESSAGE);
        }
        com.team21.uber.payment.strategy.RefundResult result =
                strategy.calculateRefund(payment, strategyRequest);
        double refundAmount = result.getAmount();
        payment.setStatus(PaymentStatus.REFUNDED);
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) {
            details = new HashMap<>();
        }
        details.put("refundAmount", refundAmount);
        details.put("refundSurgeIncluded", requestDTO.isRefundSurge());
        details.put("refundReason", requestDTO.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);
        Payment saved = paymentRepository.save(payment);
        Map<String, Object> refundedPayload = new HashMap<>();
        refundedPayload.put("paymentId", saved.getId());
        refundedPayload.put("method", saved.getMethod() != null ? saved.getMethod().name() : null);
        refundedPayload.put("amount", saved.getAmount());
        refundedPayload.put("strategyName", strategyName);
        refundedPayload.put("refundReason", requestDTO.getReason());
        refundedPayload.put("refundAmount", refundAmount);
        refundedPayload.put("refundSurgeIncluded", requestDTO.isRefundSurge());
        eventPublisher.notifyObservers("REFUNDED", refundedPayload);
        invalidatePaymentCaches(paymentId);
        RefundResponseDTO response = new RefundResponseDTO();
        response.setPaymentId(saved.getId());
        response.setRideId(saved.getRideId());
        response.setUserId(saved.getUserId());
        response.setOriginalAmount(saved.getAmount());
        response.setRefundAmount(refundAmount);
        response.setMethod(saved.getMethod());
        response.setStatus(saved.getStatus());
        response.setStrategyUsed(strategyName);
        response.setSurgeFeeIncluded(requestDTO.isRefundSurge());
        response.setTransactionDetails(saved.getTransactionDetails());
        response.setRefundedAt(LocalDateTime.now());
        return response;
    }

    @Transactional
    public Payment retryPayment(Long id) {
        Payment payment = getPaymentById(id);
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only failed payments can be retried. Current status: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        Map<String, Object> transactionDetails = payment.getTransactionDetails();
        if (transactionDetails == null) {
            transactionDetails = new HashMap<>();
        }
        Object retryAttemptObj = transactionDetails.get("retryAttempt");
        int currentRetryAttempt = 0;
        if (retryAttemptObj instanceof Number) {
            currentRetryAttempt = ((Number) retryAttemptObj).intValue();
        }
        transactionDetails.put("retryAttempt", currentRetryAttempt + 1);
        transactionDetails.put("gatewayResponse", "approved");
        payment.setTransactionDetails(transactionDetails);
        Payment saved = paymentRepository.save(payment);
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", saved.getId());
        payload.put("method", saved.getMethod() != null ? saved.getMethod().name() : null);
        payload.put("amount", saved.getAmount());
        payload.put("retryAttempt", currentRetryAttempt + 1);
        eventPublisher.notifyObservers("RETRY_ATTEMPTED", payload);
        invalidatePaymentCaches(id);
        return saved;
    }

    @Cacheable(value = "S5-F8", key = "#paymentId")
    @Transactional(readOnly = true)
    public PaymentDetailsDTO getPaymentDetails(Long paymentId) {
        Payment payment = paymentRepository.findByIdWithCoupons(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        List<AppliedCouponDTO> appliedCoupons = new ArrayList<>();
        double totalDiscount = 0.0;
        List<PaymentCoupon> paymentCoupons = payment.getPaymentCoupons();
        if (paymentCoupons != null && !paymentCoupons.isEmpty()) {
            appliedCoupons = paymentCoupons.stream()
                    .map(pc -> new AppliedCouponDTO(
                            pc.getCoupon().getCode(),
                            pc.getCoupon().getDiscountType(),
                            pc.getDiscountApplied(),
                            pc.getAppliedAt()))
                    .collect(Collectors.toList());
            totalDiscount = paymentCoupons.stream()
                    .mapToDouble(PaymentCoupon::getDiscountApplied)
                    .sum();
        }
        double finalAmount = payment.getAmount() - totalDiscount;
        return new PaymentDetailsDTO(
                payment.getId(),
                payment.getRideId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getTransactionDetails(),
                appliedCoupons,
                totalDiscount,
                finalAmount);
    }

//    M2
//    @Transactional
//    public PaymentDetailsDTO processPaymentForRide(Long rideId, PaymentRequestDTO request) {
//        String rideStatus = paymentRepository.findRideStatusById(rideId);
//        if (rideStatus == null) {
//            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId);
//        }
//        if (!rideStatus.equals("COMPLETED")) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
//                    "Ride is not completed. Current status: " + rideStatus);
//        }
//        if (paymentRepository.existsByRideIdAndStatus(rideId, PaymentStatus.COMPLETED)) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
//        }
//
//        PaymentMethod paymentMethod;
//        try {
//            if (request == null || request.getMethod() == null || request.getMethod().isBlank()) {
//                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is required");
//            }
//
////            PaymentMethod paymentMethod;
//            try {
//                paymentMethod = PaymentMethod.valueOf(request.getMethod().trim().toUpperCase());
//            } catch (IllegalArgumentException e) {
//                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
//                        "Invalid payment method: " + request.getMethod());
//            }
//        } catch (IllegalArgumentException e) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
//                    "Invalid payment method: " + request.getMethod());
//        }
//
//        if (request.getCardLastFour() != null
//                && !request.getCardLastFour().matches("\\d{4}")) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
//                    "cardLastFour must be exactly 4 digits");
//        }
//
//        List<Object[]> rideDetailsRows = paymentRepository.findRideDetailsById(rideId);
//        double amount = 0.0;
//        Long userId = 0L;
//        if (rideDetailsRows != null && !rideDetailsRows.isEmpty()) {
//            Object[] rideDetails = rideDetailsRows.get(0);
//            if (rideDetails != null && rideDetails.length > 0 && rideDetails[0] != null)
//                amount = ((Number) rideDetails[0]).doubleValue();
//            if (rideDetails != null && rideDetails.length > 1 && rideDetails[1] != null)
//                userId = ((Number) rideDetails[1]).longValue();
//        }
//
//        double surgeFee;
//        try {
//            String metadataJson = paymentRepository.findRideMetadataById(rideId);
//            if (metadataJson != null && !metadataJson.isBlank()) {
//                Map<String, Object> metadata = new com.fasterxml.jackson.databind.ObjectMapper()
//                        .readValue(metadataJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
//                Object multiplierObj = metadata.get("surgeMultiplier");
//                if (multiplierObj != null) {
//                    double surgeMultiplier = Double.parseDouble(multiplierObj.toString());
//                    if (surgeMultiplier > 1.0) {
//                        double baseFare = amount / surgeMultiplier;
//                        surgeFee = baseFare * (surgeMultiplier - 1);
//                    } else {
//                        surgeFee = 0.0;
//                    }
//                } else {
//                    surgeFee = amount * 0.15;
//                }
//            } else {
//                surgeFee = amount * 0.15;
//            }
//        } catch (Exception e) {
//            surgeFee = amount * 0.15;
//        }
//
//        Map<String, Object> transactionDetails = new HashMap<>();
//        transactionDetails.put("gatewayResponse", "APPROVED");
//        transactionDetails.put("receiptUrl", "https://receipt.example.com/ride-" + rideId);
//        transactionDetails.put("surgeFee", surgeFee);
//        if (request.getCardLastFour() != null) {
//            transactionDetails.put("cardLastFour", request.getCardLastFour());
//        }
//
//        final long finalUserId = userId;
//        final double finalAmount = amount;
//        Payment payment = paymentRepository.findByRideIdNative(rideId).orElseGet(() -> {
//            Payment p = new Payment();
//            p.setRideId(rideId);
//            p.setUserId(finalUserId);
//            p.setAmount(finalAmount);
//            p.setCreatedAt(LocalDateTime.now());
//            p.setPaymentCoupons(new ArrayList<>());
//            return p;
//        });
//
//        payment.setStatus(PaymentStatus.COMPLETED);
//        payment.setMethod(paymentMethod);
//        payment.setTransactionDetails(transactionDetails);
//
//        Payment saved = paymentRepository.save(payment);
//
//        Map<String, Object> payload = new HashMap<>();
//        payload.put("paymentId", saved.getId());
//        payload.put("method", saved.getMethod().name());
//        payload.put("amount", saved.getAmount());
//        eventPublisher.notifyObservers("COMPLETED", payload);
//        invalidatePaymentCaches(saved.getId());
//
//        return new PaymentDetailsDTO(
//                saved.getId(),
//                saved.getRideId(),
//                saved.getUserId(),
//                saved.getAmount(),
//                saved.getMethod(),
//                saved.getStatus(),
//                saved.getTransactionDetails(),
//                new ArrayList<>(),
//                0.0,
//                saved.getAmount());
//    }

//    M3

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PaymentDetailsDTO processPaymentForRide(Long rideId, PaymentRequestDTO request) {

        // 1. Feign → ride-service (replaces findRideStatusById + findRideDetailsById)
        RideServiceClient.RideResponse ride;
        try {
            log.info("Calling RideServiceClient.getRide with args={}", rideId);
            ride = rideServiceClient.getRide(rideId);
            log.info("RideServiceClient.getRide returned successfully");
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ride not found with id: " + rideId);
        } catch (FeignException e) {
            log.warn("Feign call to ride-service failed for rideId={}: {}", rideId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ride service temporarily unavailable");
        }

        // 2. Validate ride status — must be PAYMENT_PENDING (M3 saga status)
        if (!"PAYMENT_PENDING".equals(ride.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride is not awaiting payment. Current status: " + ride.status());
        }

        // 3. Find local PENDING payment (created by ride.completed consumer)
        Payment payment = paymentRepository
                .findByRideIdAndStatus(rideId, PaymentStatus.PENDING).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No pending payment for this ride"));

        // 4. Validate method
        if (request == null || request.getMethod() == null || request.getMethod().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment method is required");
        }

        PaymentMethod paymentMethod;
        try {
            paymentMethod = PaymentMethod.valueOf(request.getMethod().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Invalid method — mark FAILED, publish payment.failed
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            paymentEventPublisher.publishPaymentFailed(
                    new PaymentFailedEvent(payment.getId(), rideId,
                            "Unsupported payment method: " + request.getMethod())
            );
            log.info("Published payment.failed for rideId={}", rideId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid payment method: " + request.getMethod());
        }

        if (request.getCardLastFour() != null
                && !request.getCardLastFour().matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cardLastFour must be exactly 4 digits");
        }

        // 5. Compute surge fee from ride fare (no cross-service call needed)
        double amount = ride.amount() != null ? ride.amount() : payment.getAmount();
        double surgeFee = amount * 0.15; // default — metadata-based calc removed (cross-service SQL gone)

        // 6. Mark COMPLETED
        Map<String, Object> transactionDetails = new HashMap<>();
        transactionDetails.put("gatewayResponse", "APPROVED");
        transactionDetails.put("receiptUrl", "https://receipt.example.com/ride-" + rideId);
        transactionDetails.put("surgeFee", surgeFee);
        if (request.getCardLastFour() != null) {
            transactionDetails.put("cardLastFour", request.getCardLastFour());
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setMethod(paymentMethod);
        payment.setTransactionDetails(transactionDetails);
        Payment saved = paymentRepository.save(payment);

        // 7. Publish payment.completed (after commit)
        paymentEventPublisher.publishPaymentCompleted(
                new PaymentCompletedEvent(saved.getId(), rideId, saved.getAmount())
        );
        log.info("Published payment.completed for rideId={}", rideId);

        // 8. MongoDB observer (existing pattern — keep)
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", saved.getId());
        payload.put("method", saved.getMethod().name());
        payload.put("amount", saved.getAmount());
        eventPublisher.notifyObservers("COMPLETED", payload);
        invalidatePaymentCaches(saved.getId());

        return new PaymentDetailsDTO(
                saved.getId(),
                saved.getRideId(),
                saved.getUserId(),
                saved.getAmount(),
                saved.getMethod(),
                saved.getStatus(),
                saved.getTransactionDetails(),
                new ArrayList<>(),
                0.0,
                saved.getAmount());
    }

    private PaymentDetailsDTO toDTO(Payment payment) {
        double totalDiscount = payment.getPaymentCoupons()
                .stream()
                .mapToDouble(PaymentCoupon::getDiscountApplied)
                .sum();
        double finalAmount = payment.getAmount() - totalDiscount;
        List<AppliedCouponDTO> coupons = payment.getPaymentCoupons()
                .stream()
                .map(pc -> new AppliedCouponDTO(
                        pc.getCoupon().getCode(),
                        pc.getCoupon().getDiscountType(),
                        pc.getDiscountApplied(),
                        pc.getAppliedAt()))
                .toList();
        return new PaymentDetailsDTO(
                payment.getId(),
                payment.getRideId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getTransactionDetails(),
                coupons,
                totalDiscount,
                finalAmount);
    }

    private void invalidatePaymentCaches(Long id) {
        cacheInvalidator.evictPattern("payment-service::payment::" + id);
        cacheInvalidator.evictPattern("payment-service::S5-F1::*");
        cacheInvalidator.evictPattern("payment-service::S5-F3::*");
        cacheInvalidator.evictPattern("payment-service::S5-F6::*");
        cacheInvalidator.evictPattern("payment-service::S5-F8::" + id);
        cacheInvalidator.evictPattern("payment-service::S5-F9::*");
        cacheInvalidator.evictPattern("payment-service::S5-F10::*");
        cacheInvalidator.evictPattern("payment-service::S5-F11::*");
    }

    @RestControllerAdvice
    public class GlobalExceptionHandler {

        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .body(Map.of("message", ex.getReason()));
        }
    }


// M3 NEW ENDPOINT — GET /api/payments/user/{userId}/total?startDate=&endDate=
    public BigDecimal getUserPaymentTotal(Long userId, LocalDateTime start, LocalDateTime end) {
        Double total = paymentRepository.sumAmountByUserAndStatusAndDateRange(
                userId, PaymentStatus.COMPLETED, start, end);
        return total == null ? BigDecimal.ZERO : BigDecimal.valueOf(total);
    }

    // Batch totals: single SQL groupBy userId for many users at once
    public java.util.Map<Long, BigDecimal> getUserPaymentTotals(
            java.util.List<Long> userIds, LocalDateTime start, LocalDateTime end) {
        java.util.Map<Long, BigDecimal> result = new java.util.HashMap<>();
        if (userIds == null || userIds.isEmpty()) return result;
        for (Long uid : userIds) result.put(uid, BigDecimal.ZERO);
        List<Object[]> rows = paymentRepository.sumAmountByUsersAndStatusAndDateRange(
                userIds, PaymentStatus.COMPLETED, start, end);
        for (Object[] row : rows) {
            Long uid = ((Number) row[0]).longValue();
            BigDecimal sum = row[1] == null ? BigDecimal.ZERO
                    : new BigDecimal(row[1].toString());
            result.put(uid, sum);
        }
        return result;
    }
}