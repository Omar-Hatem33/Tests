package com.team21.uber.payment.dto;

import com.team21.uber.payment.model.PaymentMethod;
import com.team21.uber.payment.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for the S5-F12 surge-adjusted refund endpoint.
 */
public class RefundResponseDTO {

    private Long paymentId;
    private Long rideId;
    private Long userId;
    private Double originalAmount;
    private Double refundAmount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String strategyUsed;
    private boolean surgeFeeIncluded;
    private Map<String, Object> transactionDetails;
    private LocalDateTime refundedAt;

    public RefundResponseDTO() {}

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public Long getRideId() { return rideId; }
    public void setRideId(Long rideId) { this.rideId = rideId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(Double originalAmount) { this.originalAmount = originalAmount; }

    public Double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Double refundAmount) { this.refundAmount = refundAmount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getStrategyUsed() { return strategyUsed; }
    public void setStrategyUsed(String strategyUsed) { this.strategyUsed = strategyUsed; }

    public boolean isSurgeFeeIncluded() { return surgeFeeIncluded; }
    public void setSurgeFeeIncluded(boolean surgeFeeIncluded) { this.surgeFeeIncluded = surgeFeeIncluded; }

    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
    public void setTransactionDetails(Map<String, Object> transactionDetails) { this.transactionDetails = transactionDetails; }

    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
}