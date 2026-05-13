package com.team21.uber.payment.dto;

public class PaymentMethodBreakdownDTO {

    private String method;
    private Long successCount;
    private Long failureCount;
    private Double successRate;
    private Double totalAmount;

    private PaymentMethodBreakdownDTO() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PaymentMethodBreakdownDTO dto = new PaymentMethodBreakdownDTO();

        public Builder method(String method) {
            dto.method = method;
            return this;
        }

        public Builder successCount(Long successCount) {
            dto.successCount = successCount;
            return this;
        }

        public Builder failureCount(Long failureCount) {
            dto.failureCount = failureCount;
            return this;
        }

        public Builder successRate(Double successRate) {
            dto.successRate = successRate;
            return this;
        }

        public Builder totalAmount(Double totalAmount) {
            dto.totalAmount = totalAmount;
            return this;
        }

        public PaymentMethodBreakdownDTO build() {
            return dto;
        }
    }

    public String getMethod() { return method; }
    public Long getSuccessCount() { return successCount; }
    public Long getFailureCount() { return failureCount; }
    public Double getSuccessRate() { return successRate; }
    public Double getTotalAmount() { return totalAmount; }
}