package com.team21.uber.payment.dto;

public class RevenueReportDTO {
    private Double totalRevenue;
    private Long totalTransactions;
    private Double averagePayment;
    private Double refundedAmount;
    private Long refundCount;

    private RevenueReportDTO() {}

    // ── Builder ──────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Double totalRevenue;
        private Long totalTransactions;
        private Double averagePayment;
        private Double refundedAmount;
        private Long refundCount;

        public Builder totalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder totalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; return this; }
        public Builder averagePayment(Double averagePayment) { this.averagePayment = averagePayment; return this; }
        public Builder refundedAmount(Double refundedAmount) { this.refundedAmount = refundedAmount; return this; }
        public Builder refundCount(Long refundCount) { this.refundCount = refundCount; return this; }

        public RevenueReportDTO build() {
            RevenueReportDTO dto = new RevenueReportDTO();
            dto.totalRevenue = this.totalRevenue;
            dto.totalTransactions = this.totalTransactions;
            dto.averagePayment = this.averagePayment;
            dto.refundedAmount = this.refundedAmount;
            dto.refundCount = this.refundCount;
            return dto;
        }
    }

    // ── Original constructor (preserved for backward compatibility) ──────

    public RevenueReportDTO(Double totalRevenue, Long totalTransactions, Double averagePayment,
                            Double refundedAmount, Long refundCount) {
        this.totalRevenue = totalRevenue;
        this.totalTransactions = totalTransactions;
        this.averagePayment = averagePayment;
        this.refundedAmount = refundedAmount;
        this.refundCount = refundCount;
    }

    public Double getTotalRevenue() { return totalRevenue; }
    public Long getTotalTransactions() { return totalTransactions; }
    public Double getAveragePayment() { return averagePayment; }
    public Double getRefundedAmount() { return refundedAmount; }
    public Long getRefundCount() { return refundCount; }

    public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }
    public void setTotalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; }
    public void setAveragePayment(Double averagePayment) { this.averagePayment = averagePayment; }
    public void setRefundedAmount(Double refundedAmount) { this.refundedAmount = refundedAmount; }
    public void setRefundCount(Long refundCount) { this.refundCount = refundCount; }
}