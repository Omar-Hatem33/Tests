package com.team21.uber.payment.dto;

import com.team21.uber.payment.model.DiscountType;

import java.time.LocalDateTime;

public class AppliedCouponDTO {
    private String couponCode;
    private DiscountType discountType;
    private Double discountApplied;
    private LocalDateTime appliedAt;

    public AppliedCouponDTO(String couponCode, DiscountType discountType, Double discountApplied, LocalDateTime appliedAt) {
        this.couponCode = couponCode;
        this.discountType = discountType;
        this.discountApplied = discountApplied;
        this.appliedAt = appliedAt;
    }

    public String getCouponCode() { return couponCode; }
    public DiscountType getDiscountType() { return discountType; }
    public Double getDiscountApplied() { return discountApplied; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
}