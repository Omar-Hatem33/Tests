package com.team21.uber.payment.dto;

import java.util.Map;

public class UserPaymentSummaryDTO {

    private Long userId;
    private Integer totalPayments;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

    public UserPaymentSummaryDTO() {}

    public UserPaymentSummaryDTO(Long userId, Integer totalPayments, Double totalAmount, Map<String, Double> methodBreakdown) {
        this.userId = userId;
        this.totalPayments = totalPayments;
        this.totalAmount = totalAmount;
        this.methodBreakdown = methodBreakdown;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getTotalPayments() { return totalPayments; }
    public void setTotalPayments(Integer totalPayments) { this.totalPayments = totalPayments; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public Map<String, Double> getMethodBreakdown() { return methodBreakdown; }
    public void setMethodBreakdown(Map<String, Double> methodBreakdown) { this.methodBreakdown = methodBreakdown; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long userId;
        private Integer totalPayments;
        private Double totalAmount;
        private Map<String, Double> methodBreakdown;

        public Builder userId(Long v) { this.userId = v; return this; }
        public Builder totalPayments(Integer v) { this.totalPayments = v; return this; }
        public Builder totalAmount(Double v) { this.totalAmount = v; return this; }
        public Builder methodBreakdown(Map<String, Double> v) { this.methodBreakdown = v; return this; }

        public UserPaymentSummaryDTO build() {
            return new UserPaymentSummaryDTO(userId, totalPayments, totalAmount, methodBreakdown);
        }
    }
}
