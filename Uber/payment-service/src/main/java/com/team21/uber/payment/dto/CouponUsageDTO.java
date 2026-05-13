package com.team21.uber.payment.dto;

import com.team21.uber.payment.model.DiscountType;

import java.time.LocalDateTime;

public class CouponUsageDTO {
    private Long couponId;
    private String code;
    private DiscountType discountType;
    private Double discountValue;
    private Integer timesUsed;
    private Double totalDiscountGiven;
    private Boolean active;
    private Boolean expired;

    public CouponUsageDTO() {}

    public CouponUsageDTO(Long couponId, String code, DiscountType discountType,
                          Double discountValue, Integer timesUsed,
                          Double totalDiscountGiven, Boolean active,
                          LocalDateTime expiryDate) {
        this.couponId = couponId;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.timesUsed = timesUsed;
        this.totalDiscountGiven = totalDiscountGiven;
        this.active = active;
        this.expired = expiryDate != null && expiryDate.isBefore(LocalDateTime.now());
    }

    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }
    public Double getDiscountValue() { return discountValue; }
    public void setDiscountValue(Double discountValue) { this.discountValue = discountValue; }
    public Integer getTimesUsed() { return timesUsed; }
    public void setTimesUsed(Integer timesUsed) { this.timesUsed = timesUsed; }
    public Double getTotalDiscountGiven() { return totalDiscountGiven; }
    public void setTotalDiscountGiven(Double totalDiscountGiven) { this.totalDiscountGiven = totalDiscountGiven; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean expired) { this.expired = expired; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long couponId;
        private String code;
        private DiscountType discountType;
        private Double discountValue;
        private Integer timesUsed;
        private Double totalDiscountGiven;
        private Boolean active;
        private LocalDateTime expiryDate;

        public Builder couponId(Long v) { this.couponId = v; return this; }
        public Builder code(String v) { this.code = v; return this; }
        public Builder discountType(DiscountType v) { this.discountType = v; return this; }
        public Builder discountValue(Double v) { this.discountValue = v; return this; }
        public Builder timesUsed(Integer v) { this.timesUsed = v; return this; }
        public Builder totalDiscountGiven(Double v) { this.totalDiscountGiven = v; return this; }
        public Builder active(Boolean v) { this.active = v; return this; }
        public Builder expiryDate(LocalDateTime v) { this.expiryDate = v; return this; }

        public CouponUsageDTO build() {
            return new CouponUsageDTO(couponId, code, discountType, discountValue,
                    timesUsed, totalDiscountGiven, active, expiryDate);
        }
    }
}
