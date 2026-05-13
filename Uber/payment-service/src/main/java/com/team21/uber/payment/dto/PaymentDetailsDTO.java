package com.team21.uber.payment.dto;

import com.team21.uber.payment.model.PaymentMethod;
import com.team21.uber.payment.model.PaymentStatus;

import java.util.List;
import java.util.Map;


public class PaymentDetailsDTO {
    private Long paymentId;
    private Long rideId;
    private Long userId;
    private Double originalAmount;
    private PaymentMethod method;
    private PaymentStatus status;
    private Map<String, Object> transactionDetails;
    private List<AppliedCouponDTO> appliedCoupons;
    private Double totalDiscount;
    private Double finalAmount;

    private PaymentDetailsDTO() {}

    // ── Builder ──────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long paymentId;
        private Long rideId;
        private Long userId;
        private Double originalAmount;
        private PaymentMethod method;
        private PaymentStatus status;
        private Map<String, Object> transactionDetails;
        private List<AppliedCouponDTO> appliedCoupons;
        private Double totalDiscount;
        private Double finalAmount;

        public Builder paymentId(Long paymentId) { this.paymentId = paymentId; return this; }
        public Builder rideId(Long rideId) { this.rideId = rideId; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder originalAmount(Double originalAmount) { this.originalAmount = originalAmount; return this; }
        public Builder method(PaymentMethod method) { this.method = method; return this; }
        public Builder status(PaymentStatus status) { this.status = status; return this; }
        public Builder transactionDetails(Map<String, Object> transactionDetails) { this.transactionDetails = transactionDetails; return this; }
        public Builder appliedCoupons(List<AppliedCouponDTO> appliedCoupons) { this.appliedCoupons = appliedCoupons; return this; }
        public Builder totalDiscount(Double totalDiscount) { this.totalDiscount = totalDiscount; return this; }
        public Builder finalAmount(Double finalAmount) { this.finalAmount = finalAmount; return this; }

        public PaymentDetailsDTO build() {
            PaymentDetailsDTO dto = new PaymentDetailsDTO();
            dto.paymentId = this.paymentId;
            dto.rideId = this.rideId;
            dto.userId = this.userId;
            dto.originalAmount = this.originalAmount;
            dto.method = this.method;
            dto.status = this.status;
            dto.transactionDetails = this.transactionDetails;
            dto.appliedCoupons = this.appliedCoupons;
            dto.totalDiscount = this.totalDiscount;
            dto.finalAmount = this.finalAmount;
            return dto;
        }
    }

    public PaymentDetailsDTO(Long paymentId, Long rideId, Long userId, Double originalAmount,
                             PaymentMethod method, PaymentStatus status,
                             Map<String, Object> transactionDetails,
                             List<AppliedCouponDTO> appliedCoupons,
                             Double totalDiscount, Double finalAmount) {
        this.paymentId = paymentId;
        this.rideId = rideId;
        this.userId = userId;
        this.originalAmount = originalAmount;
        this.method = method;
        this.status = status;
        this.transactionDetails = transactionDetails;
        this.appliedCoupons = appliedCoupons;
        this.totalDiscount = totalDiscount;
        this.finalAmount = finalAmount;
    }

    public Long getPaymentId() { return paymentId; }
    public Long getRideId() { return rideId; }
    public Long getUserId() { return userId; }
    public Double getOriginalAmount() { return originalAmount; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
    public List<AppliedCouponDTO> getAppliedCoupons() { return appliedCoupons; }
    public Double getTotalDiscount() { return totalDiscount; }
    public Double getFinalAmount() { return finalAmount; }
}